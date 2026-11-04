package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Component;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripMessageInfo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.TripPoint;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort распознавание точек отправления/прибытия и времени поездки.
 *
 * <p>Выполняется только для сообщений канала поездок и только при сохранении/
 * редактировании сообщения, поэтому на чтение списка нагрузки не создаёт.</p>
 */
@Component
public class TripMessageParser {

    private static final Pattern DEPARTURE_PREFIX = Pattern.compile(
            "(?:^|[\\s(])(?:из|с|со)\\s+(?:[^\\s]+\\s+){0,1}$");
    private static final Pattern DESTINATION_PREFIX = Pattern.compile(
            "(?:^|[\\s(])(?:в|во|до|на)\\s+(?:[^\\s]+\\s+){0,1}$");

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
            return new TripMessageInfo(null, null, null);
        }
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        String[] points = detectPoints(value);
        LocalTime time = parseTime(value);
        if (time == null) {
            return new TripMessageInfo(points[0], points[1], null);
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
        return new TripMessageInfo(points[0], points[1], tripTime);
    }

    /**
     * Возвращает [откуда, куда]. Если явно указано только одно направление —
     * вторая точка остаётся {@code null}.
     */
    private String[] detectPoints(String text) {
        List<PointMatch> matches = findPointMatches(text);
        if (matches.isEmpty()) {
            return new String[]{null, null};
        }

        PointMatch departure = firstWithPrefix(text, matches, DEPARTURE_PREFIX);
        PointMatch destination = firstWithPrefix(text, matches, DESTINATION_PREFIX);

        if (departure != null || destination != null) {
            String from = departure == null ? null : departure.point().getLabel();
            String to = destination == null ? null : destination.point().getLabel();
            if (from != null && to == null) {
                to = otherPoint(matches, departure);
            } else if (to != null && from == null) {
                from = otherPoint(matches, destination);
            }
            if (from != null && from.equals(to)) {
                to = null;
            }
            return new String[]{from, to};
        }

        if (matches.size() >= 2) {
            return new String[]{matches.get(0).point().getLabel(), matches.get(1).point().getLabel()};
        }
        return new String[]{null, null};
    }

    private String otherPoint(List<PointMatch> matches, PointMatch selected) {
        return matches.stream()
                .filter(match -> match.point() != selected.point())
                .map(match -> match.point().getLabel())
                .findFirst()
                .orElse(null);
    }

    private PointMatch firstWithPrefix(String text, List<PointMatch> matches, Pattern prefixPattern) {
        for (PointMatch match : matches) {
            String prefix = text.substring(Math.max(0, match.start() - 40), match.start());
            if (prefixPattern.matcher(prefix).find()) {
                return match;
            }
        }
        return null;
    }

    /**
     * Ищет все упоминания известных точек. Длинные названия имеют приоритет:
     * иначе «Новочебоксарск» распознался бы ещё и как «Чебоксары».
     */
    private List<PointMatch> findPointMatches(String text) {
        List<PointMatch> candidates = new ArrayList<>();
        for (TripPoint point : TripPoint.all()) {
            for (String alias : point.getAliases()) {
                int index = text.indexOf(alias);
                if (index >= 0) {
                    candidates.add(new PointMatch(point, index, index + alias.length()));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(PointMatch::start)
                .thenComparing((first, second) -> Integer.compare(second.end(), first.end())));

        List<PointMatch> matches = new ArrayList<>();
        for (PointMatch candidate : candidates) {
            boolean overlaps = matches.stream().anyMatch(existing ->
                    candidate.start() < existing.end() && candidate.end() > existing.start());
            if (!overlaps) {
                matches.add(candidate);
            }
        }
        matches.sort(Comparator.comparingInt(PointMatch::start));
        return matches;
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

    private record PointMatch(TripPoint point, int start, int end) {
    }
}
