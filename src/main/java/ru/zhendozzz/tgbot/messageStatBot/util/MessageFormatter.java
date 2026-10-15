package ru.zhendozzz.tgbot.messageStatBot.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MessageFormatter {
    public static String trimNull(String... str) {
        return Arrays.stream(str).map(s -> Objects.nonNull(s) ? s : "").collect(Collectors.joining(" "));
    }

    public static String getDayAddition(int num) {
        int preLastDigit = num % 100 / 10;
        if (preLastDigit == 1) {
            return "дней";
        }
        switch (num % 10) {
            case 1:
                return "день";
            case 2:
            case 3:
            case 4:
                return "дня";
            default:
                return "дней";
        }

    }

    public static final String getUrlByDate(LocalDate now) {
        StringBuilder builder = new StringBuilder("https://kakoysegodnyaprazdnik.ru/baza/");
        List<String> month = List.of("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentyabr", "oktyabr", "noyabr", "dekabr");
        builder.append(month.get(now.getMonthValue() - 1));
        builder.append("/");
        builder.append(now.getDayOfMonth());
        return builder.toString();
    }

    public static String formatTripDate(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);
        return formatter.format(dateTime.toInstant(ZoneOffset.UTC));
    }

    public static String formatTripTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
        return formatter.format(dateTime.toInstant(ZoneOffset.UTC));
    }
}
