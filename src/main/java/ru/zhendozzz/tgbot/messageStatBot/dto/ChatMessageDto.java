package ru.zhendozzz.tgbot.messageStatBot.dto;

import java.time.LocalDateTime;

/**
 * Сообщение для страницы истории чата: данные из {@code log} + имя пользователя
 * из {@code users}.
 */
public interface ChatMessageDto {
    Long getId();
    LocalDateTime getDateTime();
    String getUserId();
    String getText();
    String getFirstName();
    String getLastName();
    String getUserName();
    String getAvatarPath();
    Integer getMessageId();
    /** Время последней правки или {@code null}. */
    LocalDateTime getEditedAt();
    /** Сообщение удалено ботом. */
    Boolean getDeleted();
    /** Распознанное направление поездки (только для канала попутчиков). */
    String getTripDirection();
    /** Человекочитаемое направление для бейджа. */
    String getTripDirectionLabel();
    /** Распознанное время поездки. */
    LocalDateTime getTripTime();
}
