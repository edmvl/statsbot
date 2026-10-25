package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Чекпоинт квеста: точка на карте с вопросом. {@code radiusM} — «радиус появления»:
 * внутри него вопрос открывается и ответ принимается.
 */
@Entity
@Data
@Table(name = "quest_checkpoint")
public class QuestCheckpoint {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "quest_id")
    Long questId;

    @Column(name = "sort_order")
    Integer sortOrder;

    @Column(name = "title")
    String title;

    @Column(name = "question")
    String question;

    /** Правильный ответ (сравнивается без учёта регистра и пробелов по краям). */
    @Column(name = "answer")
    String answer;

    @Column(name = "lat")
    Double lat;

    @Column(name = "lon")
    Double lon;

    @Column(name = "radius_m")
    Integer radiusM;

    /** {@code image} или {@code audio} (может быть не задано). */
    @Column(name = "media_type")
    String mediaType;

    /** Имя файла в каталоге {@code quest-{checkpointId}}. */
    @Column(name = "media_file")
    String mediaFile;
}
