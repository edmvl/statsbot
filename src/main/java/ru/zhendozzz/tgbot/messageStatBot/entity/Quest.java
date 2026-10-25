package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** Квест: набор чекпоинтов, до которых нужно дойти и ответить на вопросы. */
@Entity
@Data
@Table(name = "quest")
public class Quest {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "title")
    String title;

    @Column(name = "description")
    String description;

    /** Выключенный квест не показывается участникам (но остаётся у админа). */
    @Column(name = "active")
    Boolean active;

    /** Telegram id супер-админа, создавшего квест. */
    @Column(name = "created_by")
    String createdBy;

    @Column(name = "created_at")
    LocalDateTime createdAt;
}
