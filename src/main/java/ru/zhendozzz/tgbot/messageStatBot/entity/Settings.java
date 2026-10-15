package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Data
@Table(name = "settings")
public class Settings {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "chat_id")
    String chatId;

    @Column(name = "user_id")
    String userId;

    @Column(name = "is_admin")
    Boolean isAdmin;

    @Override
    public String toString() {
        return "Stats{" +
                "id=" + id +
                ", chatId ='" + chatId + '\'' +
                ", userId='" + userId + '\'' +
                ", isAdmin=" + isAdmin +
                '}';
    }
}
