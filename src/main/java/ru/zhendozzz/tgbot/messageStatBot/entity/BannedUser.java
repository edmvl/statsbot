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
 * Заблокированный пользователь. {@code feedbackId} — жалоба, по которой заблокировали.
 */
@Entity
@Data
@Table(name = "banned_user")
public class BannedUser {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "chat_id")
    private String chatId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "banned_by")
    private String bannedBy;

    @Column(name = "banned_at")
    private LocalDateTime bannedAt;
}
