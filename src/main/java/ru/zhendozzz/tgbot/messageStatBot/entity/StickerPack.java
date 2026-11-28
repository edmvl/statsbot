package ru.zhendozzz.tgbot.messageStatBot.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * Набор стикеров, созданный ботом командой {@code .стикер}.
 * В имени набора закодирован бот-владелец, счётчик нужен для перехода
 * на следующий набор после лимита Telegram в 120 статиков.
 */
@Entity
@Data
@Table(name = "sticker_pack")
public class StickerPack {

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "title")
    private String title;

    @Column(name = "stickers_count")
    private Integer stickersCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
