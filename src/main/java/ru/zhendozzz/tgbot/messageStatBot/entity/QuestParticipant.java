package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** Участник квеста: появляется после кнопки «Участвовать». */
@Entity
@Data
@Table(name = "quest_participant")
public class QuestParticipant {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "quest_id")
    Long questId;

    @Column(name = "user_id")
    String userId;

    @Column(name = "started_at")
    LocalDateTime startedAt;

    /** Заполняется, когда пройдены все чекпоинты. */
    @Column(name = "finished_at")
    LocalDateTime finishedAt;
}
