package ru.zhendozzz.tgbot.messageStatBot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Вложение рекламного поста для отображения в Mini App.
 */
@Data
@AllArgsConstructor
public class AdsFileView {
    private Long id;
    private String name;
    private String url;
}
