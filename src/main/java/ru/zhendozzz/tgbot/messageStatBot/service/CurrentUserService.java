package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Component;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.TelegramRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * Определяет пользователя запроса по подписанному Telegram {@code initData}.
 *
 * <p>Клиенту доверять нельзя: любой {@code userId} из формы или query-параметра
 * можно подставить. Поэтому везде, где действие выполняется «от имени пользователя»,
 * id нужно брать отсюда — он извлечён из {@code initData} и проверен по HMAC.</p>
 */
@Component
public class CurrentUserService {

    private final TelegramWebAppAuthService authService;

    public CurrentUserService(TelegramWebAppAuthService authService) {
        this.authService = authService;
    }

    /** Проверенный Telegram id или {@code null}, если подписи нет/она неверна. */
    public Long id(HttpServletRequest request) {
        return authService.getVerifiedUserId(TelegramRequest.initData(request));
    }

    /** Проверенный Telegram id строкой (в таком виде он хранится в базе) или {@code null}. */
    public String idAsString(HttpServletRequest request) {
        Long userId = id(request);
        return userId == null ? null : String.valueOf(userId);
    }

    /** true — запрос пришёл от супер-админа бота. */
    public boolean isSuperAdmin(HttpServletRequest request) {
        Long userId = id(request);
        return userId != null && AccessConstants.SUPER_ADMIN_ID.equals(userId);
    }
}
