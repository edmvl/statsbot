package ru.zhendozzz.tgbot.messageStatBot.dto;

/**
 * Подписка ({@code interested}) с данными подписчика и того, на кого он подписан.
 */
public interface InterestedViewDto {
    String getUserId();
    String getInterestId();
    String getChatId();

    String getUserFirstName();
    String getUserLastName();
    String getUserName();
    String getUserAvatarPath();

    String getInterestFirstName();
    String getInterestLastName();
    String getInterestUserName();
    String getInterestAvatarPath();
}
