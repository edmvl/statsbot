package ru.zhendozzz.tgbot.messageStatBot.message_executor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageExecutorImplTest {

    private final MessageExecutorImpl executor = new MessageExecutorImpl();

    @Test
    void removesTooLongCaptionFromMedia() {
        String shortCaption = "Короткий текст";
        String longCaption = "Очень длинный текст рекламы ".repeat(60).trim();

        assertThat(executor.captionForMedia(shortCaption)).isEqualTo(shortCaption);
        assertThat(executor.captionForMedia(longCaption)).isNull();
    }

    @Test
    void splitsLongTextIntoTelegramSizedChunks() {
        String text = ("Первая строка объявления\nВторая строка с подробностями\n").repeat(200);

        List<String> parts = executor.splitText(text);

        assertThat(parts).hasSizeGreaterThan(1);
        assertThat(parts).allMatch(part -> part.length() <= 4000);
        assertThat(String.join("", parts)).isEqualTo(text);
    }
}
