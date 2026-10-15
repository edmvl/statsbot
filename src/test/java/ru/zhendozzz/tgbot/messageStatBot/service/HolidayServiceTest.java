package ru.zhendozzz.tgbot.messageStatBot.service;

import ru.zhendozzz.tgbot.messageStatBot.util.MessageFormatter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class MessageFormatterTest {


    @Test
    void getUrlByDateTest() {
        String urlByDate = MessageFormatter.getUrlByDate(LocalDate.of(2022, 3, 25));
        Assertions.assertEquals("https://kakoysegodnyaprazdnik.ru/baza/mart/25", urlByDate);
    }

}