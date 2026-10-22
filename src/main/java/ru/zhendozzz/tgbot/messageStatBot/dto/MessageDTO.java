package ru.zhendozzz.tgbot.messageStatBot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageDTO {
    private Long chatId;
    private String chatName;
    private Integer messageId;
    private Integer replyMessageId;
    private LocalDateTime dateTime;
    private Long userId;
    private String userName;
    private String userFirstName;
    private String userLastName;
    private String userText;
    private FileDto file;
    /** Служебное событие чата (добавление/выход участника, смена названия и т.п.). */
    private boolean serviceMessage;
    /** Update пришёл как {@code edited_message} — сообщение отредактировали. */
    private boolean edited;
    /** Время правки (для edited-сообщений). */
    private LocalDateTime editDateTime;
}
