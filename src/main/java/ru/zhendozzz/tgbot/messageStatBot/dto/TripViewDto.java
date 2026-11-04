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
    /** Распознанная точка отправления или {@code null}. */
    String getTripFrom();
    /** Распознанная точка прибытия или {@code null}. */
    String getTripTo();
    /** Распознанное время поездки или {@code null}. */
    LocalDateTime getTripTime();
    /** Количество свободных или требуемых мест или {@code null}. */
    Integer getTripSeats();
    /** Автор ищет машину (тогда места — требуемые, а не свободные). */
    Boolean getTripSeekingCar();
    /** id сообщения в Telegram (для ссылки на сообщение). */
    Integer getMessageId();
}
