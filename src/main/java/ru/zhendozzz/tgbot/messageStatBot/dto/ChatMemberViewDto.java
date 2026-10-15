package ru.zhendozzz.tgbot.messageStatBot.dto;

/**
 * Участник чата для страницы администраторов бота: id + имя из {@code users}.
 */
public interface ChatMemberViewDto {
    String getUserId();
    String getName();
    String getUserName();
    String getAvatarPath();
}
