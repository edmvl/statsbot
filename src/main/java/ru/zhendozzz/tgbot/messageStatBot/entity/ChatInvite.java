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
 * Кто добавил пользователя в чат: пара {@code inviter_id} — {@code invitee_id}.
 */
@Entity
@Data
@Table(name = "chat_invite")
public class ChatInvite {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id")
    private String chatId;

    @Column(name = "inviter_id")
    private String inviterId;

    @Column(name = "invitee_id")
    private String inviteeId;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;
}
