package ru.zhendozzz.tgbot.messageStatBot.dto;

import ru.zhendozzz.tgbot.messageStatBot.util.enums.TripDirection;

import java.time.LocalDateTime;

/**
 * Распознанные из сообщения параметры поездки.
 *
 * @param direction направление или {@code null}, если не удалось распознать
 * @param tripTime  время поездки или {@code null}, если время не указано
 */
public record TripMessageInfo(TripDirection direction, LocalDateTime tripTime) {
}
