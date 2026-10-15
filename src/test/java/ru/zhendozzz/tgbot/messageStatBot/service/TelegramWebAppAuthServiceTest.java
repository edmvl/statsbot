package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Проверка алгоритма валидации Telegram WebApp initData.
 *
 * <p>Эталонный вектор посчитан независимо (Python hmac/hashlib) по алгоритму из
 * документации Telegram.</p>
 */
class TelegramWebAppAuthServiceTest {

    private static final String TOKEN = "123456789:AAExampleTokenForTesting_0123456789";

    private static final String INIT_DATA =
            "query_id=AAHdF6IQAAAAAN0XohDhrOrc"
            + "&user=%7B%22id%22%3A115965184%2C%22first_name%22%3A%22Evgeniy%22%2C%22username%22%3A%22edmvl%22%7D"
            + "&auth_date=1700000000"
            + "&hash=c9b44d7d35566d740c49c32ece7cb9e124f43d49637ae20dc0ec71c2d7253d72";

    @Test
    void verifiesValidInitData() {
        TelegramWebAppAuthService service = new TelegramWebAppAuthService(TOKEN, 0);
        assertEquals(115965184L, service.getVerifiedUserId(INIT_DATA));
    }

    @Test
    void rejectsWrongToken() {
        TelegramWebAppAuthService service = new TelegramWebAppAuthService("999:other-token", 0);
        assertNull(service.getVerifiedUserId(INIT_DATA));
    }

    @Test
    void rejectsTamperedHash() {
        TelegramWebAppAuthService service = new TelegramWebAppAuthService(TOKEN, 0);
        String tampered = INIT_DATA.replace(
                "c9b44d7d35566d740c49c32ece7cb9e124f43d49637ae20dc0ec71c2d7253d72",
                "0000000000000000000000000000000000000000000000000000000000000000");
        assertNull(service.getVerifiedUserId(tampered));
    }

    @Test
    void rejectsTamperedPayload() {
        TelegramWebAppAuthService service = new TelegramWebAppAuthService(TOKEN, 0);
        // меняем id пользователя, но оставляем прежний hash
        String tampered = INIT_DATA.replace("115965184", "1");
        assertNull(service.getVerifiedUserId(tampered));
    }

    @Test
    void rejectsBlank() {
        TelegramWebAppAuthService service = new TelegramWebAppAuthService(TOKEN, 0);
        assertNull(service.getVerifiedUserId(null));
        assertNull(service.getVerifiedUserId(""));
    }

    @Test
    void rejectsExpiredWhenMaxAgeConfigured() {
        // auth_date в векторе старый; при ненулевом max-age подпись валидна, но данные считаются просроченными
        TelegramWebAppAuthService service = new TelegramWebAppAuthService(TOKEN, 60);
        assertNull(service.getVerifiedUserId(INIT_DATA));
    }
}
