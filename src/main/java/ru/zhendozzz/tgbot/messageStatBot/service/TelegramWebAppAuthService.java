package ru.zhendozzz.tgbot.messageStatBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Проверка подлинности данных Telegram WebApp ({@code initData}).
 *
 * <p>Telegram подписывает {@code initData} HMAC-SHA256 секретом, производным от токена
 * бота, поэтому подделать его без токена нельзя. Проверка повторяет алгоритм из
 * документации Telegram: {@code secret = HMAC_SHA256("WebAppData", bot_token)},
 * {@code hash = HMAC_SHA256(secret, data_check_string)}.</p>
 */
@Slf4j
@Service
public class TelegramWebAppAuthService {

    private static final String WEB_APP_DATA_KEY = "WebAppData";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String botToken;
    private final long maxAgeSeconds;

    public TelegramWebAppAuthService(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.webapp.auth.max-age-seconds:0}") long maxAgeSeconds) {
        this.botToken = botToken;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    /**
     * Проверяет подпись {@code initData} и возвращает Telegram id пользователя.
     *
     * @return id пользователя, если подпись верна (и {@code auth_date} не просрочен при
     * включённом ограничении), иначе {@code null}.
     */
    public Long getVerifiedUserId(String initData) {
        if (initData == null || initData.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, String> fields = parse(initData);
            String providedHash = fields.get("hash");
            if (providedHash == null || providedHash.isEmpty()) {
                return null;
            }
            String dataCheckString = buildDataCheckString(fields);
            byte[] secretKey = hmac(WEB_APP_DATA_KEY.getBytes(StandardCharsets.UTF_8), botToken);
            String computedHash = hmacHex(secretKey, dataCheckString);
            if (!constantTimeEquals(providedHash, computedHash)) {
                return null;
            }
            if (maxAgeSeconds > 0 && isExpired(fields.get("auth_date"))) {
                log.warn("Telegram WebApp initData is expired");
                return null;
            }
            return extractUserId(fields.get("user"));
        } catch (Exception e) {
            log.warn("Failed to verify Telegram WebApp initData: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> parse(String initData) throws Exception {
        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            fields.put(decode(key), decode(value));
        }
        return fields;
    }

    private String decode(String value) throws Exception {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    }

    private String buildDataCheckString(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if ("hash".equals(entry.getKey())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private boolean isExpired(String authDate) {
        if (authDate == null) {
            return true;
        }
        try {
            long issuedAt = Long.parseLong(authDate);
            return (System.currentTimeMillis() / 1000 - issuedAt) > maxAgeSeconds;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private Long extractUserId(String userJson) throws Exception {
        if (userJson == null || userJson.isEmpty()) {
            return null;
        }
        JsonNode user = OBJECT_MAPPER.readTree(userJson);
        JsonNode id = user.get("id");
        return id == null ? null : id.asLong();
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(byte[] key, String data) throws Exception {
        byte[] digest = hmac(key, data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
