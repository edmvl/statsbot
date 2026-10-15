package ru.zhendozzz.tgbot.messageStatBot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import ru.zhendozzz.tgbot.messageStatBot.service.TelegramWebAppAuthService;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.TelegramRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Проверка доступа к админским страницам.
 *
 * <p>Подлинность пользователя определяется по подписанному Telegram {@code initData},
 * который страница кладёт в cookie (значение проверяется через HMAC в
 * {@link TelegramWebAppAuthService}). Пока cookie нет — отдаётся маленькая страница,
 * которая забирает {@code initData} из Telegram и перезагружает запрос уже с cookie.</p>
 */
@Slf4j
@Component
public class AdminAuthGuard {

    private final TelegramWebAppAuthService authService;
    private final SettingsService settingsService;

    public AdminAuthGuard(TelegramWebAppAuthService authService, SettingsService settingsService) {
        this.authService = authService;
        this.settingsService = settingsService;
    }

    /**
     * @param allowTripChannelAdmins пускать ли также админов канала поездок
     *                              (нужно для переписки по обращениям)
     * @return {@code true} — доступ разрешён; иначе ответ уже записан в {@code response}
     */
    public boolean allow(HttpServletRequest request, HttpServletResponse response,
                         boolean allowTripChannelAdmins) throws IOException {
        String initData = TelegramRequest.initData(request);
        if (initData == null || initData.isEmpty()) {
            writeBootstrap(response);
            return false;
        }
        Long userId = authService.getVerifiedUserId(initData);
        if (AccessConstants.SUPER_ADMIN_ID.equals(userId)) {
            return true;
        }
        if (allowTripChannelAdmins && isTripChannelAdmin(userId)) {
            return true;
        }
        log.warn("Access denied to {} for user {}", request.getRequestURI(), userId);
        response.setHeader("Cache-Control", "no-store");
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return false;
    }

    /** Админ чата, в котором ведётся канал поездок (там же живут обращения). */
    private boolean isTripChannelAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        String chatId = settingsService.findTripChannelId();
        if (chatId == null) {
            return false;
        }
        try {
            return settingsService.isUserAdmin(Long.valueOf(chatId), userId);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void writeBootstrap(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(BOOTSTRAP_HTML);
    }

    private static final String BOOTSTRAP_HTML = "<!DOCTYPE html>\n"
            + "<html lang=\"ru\">\n"
            + "<head>\n"
            + "    <meta charset=\"UTF-8\">\n"
            + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
            + "    <title>Проверка доступа</title>\n"
            + "    <script src=\"/js/telegram-web-app.js\"></script>\n"
            + "</head>\n"
            + "<body>\n"
            + "<script>\n"
            + "(function () {\n"
            + "    var data = window.Telegram && window.Telegram.WebApp ? window.Telegram.WebApp.initData : '';\n"
            + "    if (!data) {\n"
            + "        document.body.textContent = 'Доступ только через Telegram.';\n"
            + "        return;\n"
            + "    }\n"
            + "    var secure = location.protocol === 'https:' ? '; secure' : '';\n"
            + "    document.cookie = '" + TelegramRequest.AUTH_COOKIE + "=' + data + '; path=/; samesite=lax' + secure;\n"
            + "    location.replace(location.href);\n"
            + "})();\n"
            + "</script>\n"
            + "</body>\n"
            + "</html>\n";
}
