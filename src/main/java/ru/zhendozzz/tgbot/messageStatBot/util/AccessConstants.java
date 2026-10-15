package ru.zhendozzz.tgbot.messageStatBot.util;

/**
 * Права доступа к админским функциям бота.
 */
public final class AccessConstants {

    /**
     * Telegram id владельца бота. Только этому пользователю доступны
     * админские кнопки и страницы (настройки чата, администраторы бота).
     */
    public static final Long SUPER_ADMIN_ID = 115965184L;

    private AccessConstants() {
    }
}
