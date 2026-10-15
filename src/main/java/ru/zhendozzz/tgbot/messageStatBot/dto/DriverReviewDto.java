package ru.zhendozzz.tgbot.messageStatBot.dto;

/**
 * Отзыв о водителе: оценка и комментарий + данные автора отзыва из {@code users}.
 */
public interface DriverReviewDto {
    Long getId();
    Integer getStars();
    String getComment();
    String getReviewUserId();
    String getFirstName();
    String getLastName();
    String getUserName();
    String getAvatarPath();
}
