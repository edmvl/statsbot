package ru.zhendozzz.tgbot.messageStatBot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Подписки одного пользователя: сам подписчик и список тех, на кого он подписан.
 * Нужно, чтобы на странице не дублировать данные подписчика для каждой подписки.
 */
@Data
@AllArgsConstructor
public class SubscriptionGroupDto {
    private String userId;
    private String firstName;
    private String lastName;
    private String userName;
    private String avatarPath;
    private List<InterestedViewDto> interests;
}
