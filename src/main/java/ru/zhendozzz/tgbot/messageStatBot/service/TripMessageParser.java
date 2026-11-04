package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Component;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripMessageInfo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.TripDirection;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort распознавание направления и времени поездки из текста сообщения.
 *
 * <p>Выполняется только для сообщений канала поездок и только при сохранении/
 * редактировании сообщения, поэтому на чтение списка нагрузки не создаёт.</p>
 */
@Component
public class TripMessageParser {

    private static final String CITY = "(чебокс\\p{L}*|чбк|cheb\\p{L}*|урмар\\p{L}*|урм\\p{L}*|urmar\\p{L}*)";
    private static final String CHEB = "(чебокс\\p{L}*|чбк|cheb\\p{L}*)";
    private static final String URM = "(урмар\\p{L}*|урм\\p{L}*|urmar\\p{L}*)";

    private static final Pattern DEPARTURE = Pattern.compile(
            "(?:^|[\\s(])(?:из|с|со)\\s+(?:[^\\s]+\\s+){0,1}?" + CITY);
    private static final Pattern DESTINATION = Pattern.compile(
            "(?:^|[\\s(])(?:в|во|до|на)\\s+(?:[^\\s]+\\s+){0,1}?" + CITY);

    private static final Pattern TIME_WORD = Pattern.compile(
            "(^|[^\\p{L}\\d])([01]?\\d|2[0-3])\\s*(?:час(?:ов|а)?\\s*)?(утра|дня|вечера|ночи)");
    private static final Pattern TIME_COLON = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])\\s*[:.]\\s*([0-5]\\d)(?!\\d)");
    private static final Pattern TIME_HYPHEN = Pattern.compile(
            "(?<![\\d.])([01]?\\d|2[0-3])\\s*[-–—]\\s*([0-5]\\d)(?!\\d)");
    private static final Pattern TIME_PREPOSITION = Pattern.compile(
            "(?:^|[\\s(])(?:в|к|на)\\s+([01]?\\d|2[0-3])"
                    + "(?=\\s*(?:$|[,.;!?)]|час|утра|дня|вечера|ночи))");

    private static final Pattern DATE_WITH_MONTH = Pattern.compile(
            "([0-3]?\\d)\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|"
                    + "сентября|октября|ноября|декабря)");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3),
            Map.entry("апреля", 4), Map.entry("мая", 5), Map.entry("июня", 6),
            Map.entry("июля", 7), Map.entry("августа", 8), Map.entry("сентября", 9),
            Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12));

    private static final Map<DayOfWeek, String[]> WEEKDAYS = Map.of(
            DayOfWeek.MONDAY, new String[]{"понедельник", "пн"},
            DayOfWeek.TUESDAY, new String[]{"вторник", "вт"},
            DayOfWeek.WEDNESDAY, new String[]{"среда", "среду", "ср"},
            DayOfWeek.THURSDAY, new String[]{"четверг", "чт"},
            DayOfWeek.FRIDAY, new String[]{"пятница", "пятницу", "пт"},
            DayOfWeek.SATURDAY, new String[]{"суббота", "субботу", "сб"},
            DayOfWeek.SUNDAY, new String[]{"воскресенье", "вс"});

    public TripMessageInfo parse(String text, LocalDateTime messageTime) {
        if (text == null || text.trim().isEmpty() || messageTime == null) {
            return new TripMessageInfo(null, null);
        }
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        TripDirection direction = detectDirection(value);
        LocalTime time = parseTime(value);
        if (time == null) {
            return new TripMessageInfo(direction, null);
        }

        LocalDate baseDate = messageTime.toLocalDate();
        LocalDate date = baseDate;
        boolean hasExplicitDate = false;
        boolean isWeekday = false;

        if (value.contains("послезавтра")) {
            date = baseDate.plusDays(2);
            hasExplicitDate = true;
        } else if (value.contains("завтра")) {
            date = baseDate.plusDays(1);
            hasExplicitDate = true;
        } else if (value.contains("сегодня")) {
            hasExplicitDate = true;
        } else {
            Matcher monthMatcher = DATE_WITH_MONTH.matcher(value);
            if (monthMatcher.find()) {
                int day = Integer.parseInt(monthMatcher.group(1));
                int month = MONTHS.get(monthMatcher.group(2));
                try {
                    date = LocalDate.of(baseDate.getYear(), month, day);
                    if (date.isBefore(baseDate)) {
                        date = date.plusYears(1);
                    }
                    hasExplicitDate = true;
                } catch (java.time.DateTimeException e) {
                    // Некорректная дата — считаем, что дата не указана.
                }
            } else {
                DayOfWeek weekday = findWeekday(value);
                if (weekday != null) {
                    date = baseDate.with(TemporalAdjusters.nextOrSame(weekday));
                    isWeekday = true;
                }
            }
        }

        LocalDateTime tripTime = LocalDateTime.of(date, time);
        if (isWeekday && !tripTime.isAfter(messageTime)) {
            tripTime = tripTime.plusWeeks(1);
        } else if (!hasExplicitDate && !tripTime.isAfter(messageTime)) {
            // Время уже прошло — скорее всего, поездка на следующий день.
            tripTime = tripTime.plusDays(1);
        }
        return new TripMessageInfo(direction, tripTime);
    }

    private TripDirection detectDirection(String text) {
        Matcher departure = DEPARTURE.matcher(text);
        Matcher destination = DESTINATION.matcher(text);
        String from = departure.find() ? departure.group(1) : null;
        String to = destination.find() ? destination.group(1) : null;

        TripDirection byPrepositions = directionOf(from, to);
        if (byPrepositions != null) {
            return byPrepositions;
        }

        int cheb = firstIndex(text, CHEB);
        int urm = firstIndex(text, URM);
        if (cheb >= 0 && urm >= 0) {
            return cheb < urm ? TripDirection.CHEBOKSARY_URMARY : TripDirection.URMARY_CHEBOKSARY;
        }
        return null;
    }

    private TripDirection directionOf(String from, String to) {
        boolean fromCheb = from != null && from.matches(CHEB);
        boolean fromUrm = from != null && from.matches(URM);
        boolean toCheb = to != null && to.matches(CHEB);
        boolean toUrm = to != null && to.matches(URM);

        if (fromCheb && (toUrm || to == null)) {
            return TripDirection.CHEBOKSARY_URMARY;
        }
        if (fromUrm && (toCheb || to == null)) {
            return TripDirection.URMARY_CHEBOKSARY;
        }
        if (toCheb && from == null) {
            return TripDirection.URMARY_CHEBOKSARY;
        }
        if (toUrm && from == null) {
            return TripDirection.CHEBOKSARY_URMARY;
        }
        return null;
    }

    private int firstIndex(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private LocalTime parseTime(String text) {
        Matcher word = TIME_WORD.matcher(text);
        if (word.find()) {
            int hour = Integer.parseInt(word.group(2));
            hour = applyDayPart(hour, word.group(3));
            return hour > 23 ? null : LocalTime.of(hour, 0);
        }

        Matcher colon = TIME_COLON.matcher(text);
        if (colon.find()) {
            return time(colon, colon.group(1), colon.group(2), text, colon.end());
        }

        Matcher hyphen = TIME_HYPHEN.matcher(text);
        if (hyphen.find()) {
            return time(hyphen, hyphen.group(1), hyphen.group(2), text, hyphen.end());
        }

        Matcher preposition = TIME_PREPOSITION.matcher(text);
        if (preposition.find()) {
            int hour = Integer.parseInt(preposition.group(1));
            hour = applyDayPart(hour, text.substring(preposition.end(), Math.min(text.length(), preposition.end() + 12)));
            return hour > 23 ? null : LocalTime.of(hour, 0);
        }
        return null;
    }

    private LocalTime time(Matcher matcher, String hourGroup, String minuteGroup,
                           String text, int end) {
        int hour = Integer.parseInt(hourGroup);
        int minute = Integer.parseInt(minuteGroup);
        String tail = text.substring(end, Math.min(text.length(), end + 12));
        hour = applyDayPart(hour, tail);
        if (hour > 23 || minute > 59) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private int applyDayPart(int hour, String tail) {
        if (tail == null) {
            return hour;
        }
        if (tail.contains("вечера") || tail.contains("дня")) {
            return hour < 12 ? hour + 12 : hour;
        }
        if (tail.contains("ночи")) {
            return hour == 12 ? 0 : hour;
        }
        return hour;
    }

    private DayOfWeek findWeekday(String text) {
        for (Map.Entry<DayOfWeek, String[]> entry : WEEKDAYS.entrySet()) {
            for (String name : entry.getValue()) {
                if (containsWord(text, name)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean containsWord(String text, String word) {
        return Pattern.compile("(?:^|[^\\p{L}])" + Pattern.quote(word) + "(?:$|[^\\p{L}])")
                .matcher(text)
                .find();
    }
}
