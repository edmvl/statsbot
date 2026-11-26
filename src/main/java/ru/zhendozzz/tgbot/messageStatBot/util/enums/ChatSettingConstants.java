package ru.zhendozzz.tgbot.messageStatBot.util.enums;

import lombok.Getter;

@Getter
public enum ChatSettingConstants {
    ENABLE_SELECTED("enable_selected"),
    ENABLE_HOLIDAYS("enable_holidays"),
    ENABLE_STATS("enable_stats"),
    ENABLE_REMINDER("enable_reminder"),
    TRIP_CHANNEL("trip_channel"),
    ENABLE_MEMBER_CONTROL("enable_member_control"),
    ENABLE_ADS("enable_ads"),
    ENABLE_BOT_MENTION_FILTER("enable_bot_mention_filter"),
    /** Ежедневное саммари переписки (DeepSeek) в 00:30. */
    ENABLE_SUMMARY("enable_summary"),
    /** Служебный флаг: Telegram ответил «chat not found», рассылки в чат остановлены. */
    CHAT_UNAVAILABLE("chat_unavailable", true),
    ;


    private final String dbValue;
    /** Служебная настройка: не показывается в интерфейсе и не управляется из Mini App. */
    private final boolean internal;

    ChatSettingConstants(String dbValue) {
        this(dbValue, false);
    }

    ChatSettingConstants(String dbValue, boolean internal) {
        this.dbValue = dbValue;
        this.internal = internal;
    }

}
