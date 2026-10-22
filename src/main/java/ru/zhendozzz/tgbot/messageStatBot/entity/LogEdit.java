package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * Предыдущая версия отредактированного сообщения. Последняя версия живёт в
 * {@link Log}, а каждая правка складывает сюда то, что было до неё.
 */
@Entity
@Data
@Table(name = "log_edit")
public class LogEdit {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** Строка {@code log}, к которой относится версия. */
    @Column(name = "log_id")
    Long logId;

    @Column(name = "text")
    String text;

    @Column(name = "filetype")
    String fileType;

    @Column(name = "file")
    String file;

    /** Когда версия перестала быть актуальной. */
    @Column(name = "edited_at")
    LocalDateTime editedAt;

}
