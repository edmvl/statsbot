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
    /** Служебный флаг: Telegram ответил «chat not found», рассылки в чат остановлены. */
    CHAT_UNAVAILABLE("chat_unavailable"),
    ;


    private final String dbValue;

    ChatSettingConstants(String dbValue) {
        this.dbValue = dbValue;
    }

}
