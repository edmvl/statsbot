package ru.zhendozzz.tgbot.messageStatBot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import ru.zhendozzz.tgbot.messageStatBot.service.TelegramWebAppAuthService;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.TelegramRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
        if (userId == null) {
            // Cookie есть, но подпись не сходится (например, бот-токен перевыпустили).
            // Показываем страницу обновления доступа вместо «глухого» 403.
            writeBootstrap(response);
            return false;
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

    /**
     * Доступ к истории чатов, настройкам и администраторам.
     *
     * <p>Супер-админ видит все чаты. Обычный администратор — только те чаты,
     * в которых он назначен администратором бота. Для страницы списка чатов
     * достаточно быть админом хотя бы одного чата, а для страниц конкретного
     * чата проверяется именно его id.</p>
     *
     * @return {@code true} — доступ разрешён; иначе ответ уже записан в {@code response}
     */
    public boolean allowChatAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String initData = TelegramRequest.initData(request);
        if (initData == null || initData.isEmpty()) {
            writeBootstrap(response);
            return false;
        }
        Long userId = authService.getVerifiedUserId(initData);
        if (AccessConstants.SUPER_ADMIN_ID.equals(userId)) {
            return true;
        }
        if (userId != null && canAccessChatArea(request, userId)) {
            return true;
        }
        if (userId == null) {
            // Подпись не сходится — перевыпускаем cookie через страницу обновления доступа.
            writeBootstrap(response);
            return false;
        }
        log.warn("Chat access denied to {} for user {}", request.getRequestURI(), userId);
        response.setHeader("Cache-Control", "no-store");
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return false;
    }

    private boolean canAccessChatArea(HttpServletRequest request, Long userId) {
        String chatId = resolveChatId(request);
        if (chatId != null) {
            return settingsService.isUserAdmin(chatId, userId);
        }
        // Список чатов: показываем только те, где пользователь администратор.
        String path = requestPath(request);
        if ("/app/chats".equals(path) || "/app/chats/".equals(path)) {
            return settingsService.isAdminOfAnyChat(userId);
        }
        return false;
    }

    /**
     * id чата из path-переменной ({@code /app/chat/{chatId}}), параметра формы
     * (запуск розыгрыша) или, как запасной вариант, из адреса запроса.
     */
    private String resolveChatId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map) {
            Object chatId = ((Map<?, ?>) attribute).get("chatId");
            if (chatId != null && !chatId.toString().trim().isEmpty()) {
                return chatId.toString().trim();
            }
        }
        String parameter = request.getParameter("chatId");
        if (parameter != null && !parameter.trim().isEmpty()) {
            return parameter.trim();
        }
        String path = requestPath(request);
        for (String prefix : CHAT_PATH_PREFIXES) {
            if (path.startsWith(prefix) && path.length() > prefix.length()) {
                String tail = path.substring(prefix.length());
                int slash = tail.indexOf('/');
                String value = slash < 0 ? tail : tail.substring(0, slash);
                return value.trim().isEmpty() ? null : value.trim();
            }
        }
        return null;
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    private static final String[] CHAT_PATH_PREFIXES = {
            "/app/chat-settings/", "/app/chat-admins/", "/app/chat/"
    };

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
            + "    var url = new URL(location.href);\n"
            + "    if (url.searchParams.has('_auth_try')) {\n"
            + "        // Повторная попытка не помогла — не зацикливаем перезагрузку.\n"
            + "        document.body.textContent = 'Не удалось подтвердить доступ. Откройте страницу заново.';\n"
            + "        return;\n"
            + "    }\n"
            + "    var secure = location.protocol === 'https:' ? '; secure' : '';\n"
            + "    document.cookie = '" + TelegramRequest.AUTH_COOKIE + "=' + data + '; path=/; samesite=lax' + secure;\n"
            + "    url.searchParams.set('_auth_try', '1');\n"
            + "    location.replace(url.toString());\n"
            + "})();\n"
            + "</script>\n"
            + "</body>\n"
            + "</html>\n";
}
