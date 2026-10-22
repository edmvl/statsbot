package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "log")
public class Log {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "chat_id")
    String chatId;

    @Column(name = "date_time")
    LocalDateTime dateTime;

    @Column(name = "user_id")
    String userId;

    @Column(name = "chat_name")
    String chatName;

    @Column(name = "text")
    String text;

    @Column(name = "filetype")
    String fileType;

    @Column(name = "file")
    String file;

    @Column(name = "logger_type")
    String loggerType;

    /** id сообщения в Telegram — по нему находим строку при редактировании и удалении. */
    @Column(name = "message_id")
    Integer messageId;

    /** Время последней правки; {@code null} — сообщение не редактировали. */
    @Column(name = "edited_at")
    LocalDateTime editedAt;

    /** Сообщение удалено ботом (например, фильтром слов). */
    @Column(name = "deleted")
    Boolean deleted;

}
