package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "feedback")
public class FeedBack {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id")
    String userId;

    @Column(name = "user_name")
    String userName;

    @Column(name = "date_time")
    LocalDateTime dateTime;

    @Column(name = "description")
    String description;

    @Column(name = "finished")
    Boolean finished;

    /** Пользователь, на которого жалуются (может быть не указан). */
    @Column(name = "target_user_id")
    String targetUserId;

    @Column(name = "target_user_name")
    String targetUserName;

}
