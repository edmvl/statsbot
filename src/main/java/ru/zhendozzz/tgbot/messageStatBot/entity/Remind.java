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
@Table(name = "remind")
public class Remind {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id")
    String userId;

    @Column(name = "chat_id")
    String chatId;

    @Column(name = "reply_message_id")
    String replyMessageId;

    @Column(name = "date_time")
    LocalDateTime dateTime;

    @Column(name = "text")
    String text;

    @Column(name = "active")
    boolean active;

}
