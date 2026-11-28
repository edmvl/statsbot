package ru.zhendozzz.tgbot.messageStatBot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Загрузка стикеров в набор напрямую через Bot API.
 *
 * <p>Зачем свой HTTP: в telegrambots 6.9.7.1 метод {@code addStickerToSet} собирает
 * multipart с полем {@code sticker} как <b>массив</b> (общий код с {@code createNewStickerSet},
 * где поле называется {@code stickers}), и Telegram отвечает
 * «can't parse sticker: InputSticker must be an Object». Здесь {@code sticker}
 * отправляется объектом, как требует Bot API.</p>
 */
@Service
@Slf4j
public class StickerUploader {

    private static final String API_URL = "https://api.telegram.org/bot";
    private static final String METHOD = "/addStickerToSet";
    /** Имя вложения: на него ссылается {@code attach://sticker.png} в JSON. */
    private static final String FILE_NAME = "sticker.png";
    private static final String STICKER_FORMAT = "static";
    private static final String DEFAULT_EMOJI = "🖼";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telegram.bot.token}")
    private String token;

    /**
     * Добавляет PNG в существующий набор.
     *
     * @return {@code true}, если Telegram подтвердил добавление
     */
    public boolean addStickerToSet(long ownerUserId, String packName, String emoji, byte[] png) {
        if (Objects.isNull(token) || token.isBlank() || Objects.isNull(png) || png.length == 0) {
            return false;
        }
        try {
            String boundary = "----statsbot" + System.nanoTime();
            byte[] body = buildBody(boundary, ownerUserId, packName, emoji, png);
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL + token + METHOD))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String answer = Objects.toString(response.body(), "");
            if (response.statusCode() == 200 && answer.contains("\"ok\":true")) {
                return true;
            }
            log.error("addStickerToSet {}: [{}] {}", packName, response.statusCode(), answer);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("addStickerToSet {}: прервано", packName);
            return false;
        } catch (Exception e) {
            log.error("addStickerToSet {}: {}", packName, e.getMessage());
            return false;
        }
    }

    /** Тело multipart-запроса: user_id, name, sticker (JSON-объект) и файл PNG. */
    byte[] buildBody(String boundary, long ownerUserId, String packName, String emoji, byte[] png)
            throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "user_id", String.valueOf(ownerUserId));
        writeField(body, boundary, "name", packName);
        writeField(body, boundary, "sticker", stickerJson(emoji));
        writeFile(body, boundary, png);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private String stickerJson(String emoji) throws IOException {
        String stickerEmoji = Objects.isNull(emoji) || emoji.isBlank() ? DEFAULT_EMOJI : emoji;
        return objectMapper.writeValueAsString(Map.of(
                "sticker", "attach://" + FILE_NAME,
                "format", STICKER_FORMAT,
                "emoji_list", List.of(stickerEmoji)
        ));
    }

    private void writeField(ByteArrayOutputStream body, String boundary, String name, String value)
            throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(ByteArrayOutputStream body, String boundary, byte[] png) throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + FILE_NAME + "\"; filename=\"" + FILE_NAME + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(png);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
