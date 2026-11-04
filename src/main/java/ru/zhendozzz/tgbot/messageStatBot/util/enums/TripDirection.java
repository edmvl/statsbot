package ru.zhendozzz.tgbot.messageStatBot.util.enums;

import lombok.Getter;

/** Направление поездки в канале попутчиков. */
public enum TripDirection {

    CHEBOKSARY_URMARY("Чебоксары-Урмары"),
    URMARY_CHEBOKSARY("Урмары-Чебоксары");

    @Getter
    private final String label;

    TripDirection(String label) {
        this.label = label;
    }

    public static TripDirection fromDb(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TripDirection.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
