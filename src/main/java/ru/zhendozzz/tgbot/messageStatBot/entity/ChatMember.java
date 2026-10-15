package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "chat_member")
public class ChatMember {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "chat_id")
    private String chatId;

    @Column(name = "last_message")
    private LocalDateTime lastMessage;

    public ChatMember(String userId, String chatId, LocalDateTime lastMessage) {
        this.userId = userId;
        this.chatId = chatId;
        this.lastMessage = lastMessage;
    }

    public ChatMember() {
    }
}