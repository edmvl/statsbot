package ru.zhendozzz.tgbot.messageStatBot.dto;

/**
 * Личный чат с ботом для админской страницы: последнее сообщение и данные автора
 * из {@code users}. Приватные чаты в Telegram имеют положительный id, равный id пользователя.
 */
public interface PrivateChatDto {
    String getChatId();
    String getFirstName();
    String getLastName();
    String getUserName();
    String getAvatarPath();
    /** Время последнего сообщения в формате {@code DD.MM.YYYY HH:MI}. */
    String getLastMessage();
    String getLastText();
    Long getMessageCount();
}
