package ru.zhendozzz.tgbot.messageStatBot.dto;

import java.time.LocalDateTime;

/**
 * Пользователь в контексте приглашения: кто добавил или кого добавили.
 */
public interface ChatInviteViewDto {
    String getUserId();
    String getName();
    String getUserName();
    String getAvatarPath();
    LocalDateTime getInvitedAt();
}
