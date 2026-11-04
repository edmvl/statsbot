package ru.zhendozzz.tgbot.messageStatBot.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TripViewDto {
    Long getLogId();
    String getUserId();
    LocalDateTime getDateTime();
    String getFirstName();
    String getLastName();
    String getUserName();
    String getText();
    BigDecimal getRating();
    String getCnt();
    String getComments();
    String getAvatarPath();
    /** Время последней правки сообщения или {@code null}. */
    LocalDateTime getEditedAt();
    /** Сообщение удалено ботом. */
    Boolean getDeleted();
    /** Распознанное направление поездки (значение {@code TripDirection}) или {@code null}. */
    String getTripDirection();
    /** Человекочитаемое направление для бейджа. */
    String getTripDirectionLabel();
    /** Распознанное время поездки или {@code null}. */
    LocalDateTime getTripTime();
}
