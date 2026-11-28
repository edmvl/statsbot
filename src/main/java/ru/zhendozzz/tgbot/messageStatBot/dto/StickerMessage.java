package ru.zhendozzz.tgbot.messageStatBot.dto;

import lombok.Builder;
import lombok.Getter;

import java.awt.image.BufferedImage;

/**
 * Сообщение в том виде, в котором оно попадёт на стикер: автор, его статус в чате,
 * время и текст. Рисуется «как в приложении» — аватар, имя, пузырь с сообщением.
 */
@Getter
@Builder
public class StickerMessage {

    /** Имя автора: «Иван Петров», «@username» или «Аноним». */
    private final String authorName;

    /** Статус в чате: «владелец», «админ», «канал» или {@code null}. */
    private final String status;

    /** Время отправки, HH:mm. */
    private final String time;

    private final String text;

    /** Аватар автора (может быть {@code null} — тогда рисуем инициалы). */
    private final BufferedImage avatar;
}
