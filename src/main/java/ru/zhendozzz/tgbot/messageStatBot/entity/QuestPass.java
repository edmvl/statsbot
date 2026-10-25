package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** Пройденный чекпоинт участника (один раз на чекпоинт). */
@Entity
@Data
@Table(name = "quest_pass")
public class QuestPass {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "participant_id")
    Long participantId;

    @Column(name = "checkpoint_id")
    Long checkpointId;

    @Column(name = "answer")
    String answer;

    @Column(name = "passed_at")
    LocalDateTime passedAt;
}
