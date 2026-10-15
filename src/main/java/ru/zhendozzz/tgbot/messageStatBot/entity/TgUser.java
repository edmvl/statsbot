package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * Текущие данные пользователя Telegram (таблица {@code users}).
 *
 * <p>ФИО/ник обновляются при каждом сообщении; изменения пишутся в
 * {@link UserNameHistory}. Аватар хранится в файловой системе, в БД — путь
 * и идентификатор файла (чтобы обновлять только при смене).</p>
 */
@Entity
@Data
@Table(name = "users")
public class TgUser {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "avatar_path")
    private String avatarPath;

    @Column(name = "avatar_file_id")
    private String avatarFileId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
