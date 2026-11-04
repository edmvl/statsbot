package ru.zhendozzz.tgbot.messageStatBot.dto;

import java.time.LocalDateTime;

/**
 * Распознанные из сообщения параметры поездки.
 *
 * @param from       точка отправления или {@code null}
 * @param to         точка прибытия или {@code null}
 * @param tripTime   время поездки или {@code null}, если время не указано
 * @param seekingCar автор ищет машину/попутку (а не предлагает поездку)
 */
public record TripMessageInfo(String from, String to, LocalDateTime tripTime, boolean seekingCar) {
}
