package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StickerUploaderTest {

    private static final String PACK = "hamoryal_by_hamoryalstatsbot";
    private static final byte[] PNG = {1, 2, 3, 4};

    private final StickerUploader uploader = new StickerUploader();

    @Test
    void sendsStickerAsJsonObjectWithFormatAndEmoji() throws Exception {
        String body = body(uploader.buildBody("BND", 115965184L, PACK, "😍", PNG));

        assertThat(body).contains("name=\"user_id\"");
        assertThat(body).contains("115965184");
        assertThat(body).contains("name=\"name\"");
        assertThat(body).contains(PACK);
        assertThat(body).contains("name=\"sticker\"");
        // Ключевое отличие от библиотеки: объект, а не массив.
        assertThat(body).contains("\"sticker\":\"attach://sticker.png\"");
        assertThat(body).contains("\"format\":\"static\"");
        assertThat(body).contains("\"emoji_list\":[\"😍\"]");
        assertThat(body).doesNotContain("[{");
    }

    @Test
    void attachesPngAsFilePart() throws Exception {
        byte[] raw = uploader.buildBody("BND", 1L, PACK, "😍", PNG);
        String body = body(raw);

        assertThat(body).contains("name=\"sticker.png\"; filename=\"sticker.png\"");
        assertThat(body).contains("Content-Type: application/octet-stream");
        assertThat(body).endsWith("--BND--\r\n");
        assertThat(raw).containsSequence(PNG);
    }

    @Test
    void fallsBackToDefaultEmoji() throws Exception {
        String body = body(uploader.buildBody("BND", 1L, PACK, null, PNG));

        assertThat(body).contains("🖼");
    }

    private String body(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8);
    }
}
