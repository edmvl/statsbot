package ru.zhendozzz.tgbot.messageStatBot.util;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

/**
 * Извлечение Telegram WebApp {@code initData} из запроса.
 *
 * <p>Данные могут прийти cookie (её ставит страница/защита админки), заголовком
 * или query-параметром.</p>
 */
public final class TelegramRequest {

    public static final String AUTH_COOKIE = "tg_webapp_auth";
    public static final String AUTH_HEADER = "X-Telegram-Init-Data";
    public static final String AUTH_PARAM = "_auth";

    public static String initData(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AUTH_COOKIE.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                    return cookie.getValue();
                }
            }
        }
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && !header.isEmpty()) {
            return header;
        }
        return request.getParameter(AUTH_PARAM);
    }

    private TelegramRequest() {
    }
}
