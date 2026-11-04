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
    /** Распознанная точка отправления (только для канала попутчиков). */
    String getTripFrom();
    /** Распознанная точка прибытия. */
    String getTripTo();
    /** Распознанное время поездки. */
    LocalDateTime getTripTime();
}
