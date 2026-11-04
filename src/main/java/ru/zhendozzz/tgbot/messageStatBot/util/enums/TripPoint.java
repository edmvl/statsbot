package ru.zhendozzz.tgbot.messageStatBot.util.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * Известные точки маршрутов. Список можно расширять: распознанные точки
 * автоматически появляются в фильтрах на странице «Поездки».
 */
public enum TripPoint {

    URMARY("Урмары", "урмар"),
    CHEBOKSARY("Чебоксары", "чебоксар", "чебокс", "чбк", "cheb"),
    NOVOCHEBOKSARY("Новочебоксарск", "новочебоксарск", "новочебоксар", "новочеб"),
    KANASH("Канаш", "канаш"),
    TSIVILSK("Цивильск", "цивильск", "цивиль"),
    KUGESI("Кугеси", "кугеси", "кугесь"),
    MARIINSKY_POSAD("Мариинский Посад", "мариинский посад", "мариинск"),
    KOZLOVKA("Козловка", "козловк"),
    YADRIN("Ядрин", "ядрин"),
    SHUMERLYA("Шумерля", "шумерл"),
    ALATYR("Алатырь", "алатыр"),
    BATYREVO("Батырево", "батырев", "батарев"),
    KOMSOMOLSKOE("Комсомольское", "комсомольск"),
    YALCHIKI("Яльчики", "яльчик"),
    VURNARY("Вурнары", "вурнар"),
    IBRESI("Ибреси", "ибрес"),
    YANTIKOVO("Янтиково", "янтиков"),
    KRASNOARMEYSKOE("Красноармейское", "красноармейск");

    @Getter
    private final String label;

    @Getter
    private final List<String> aliases;

    TripPoint(String label, String... aliases) {
        this.label = label;
        this.aliases = List.of(aliases);
    }

    /** Точки, которые показываем в фильтрах даже до первого распознанного сообщения. */
    public static List<String> defaultLabels() {
        return List.of(URMARY.label, CHEBOKSARY.label, NOVOCHEBOKSARY.label);
    }

    public static List<TripPoint> all() {
        return Arrays.asList(values());
    }
}
