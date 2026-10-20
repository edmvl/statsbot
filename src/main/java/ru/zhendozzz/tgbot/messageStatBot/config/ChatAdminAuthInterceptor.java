package ru.zhendozzz.tgbot.messageStatBot.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Доступ к истории и настройкам чатов.
 *
 * <p>Супер-админ видит все чаты, администратор чата — только свой чат:
 * историю сообщений, настройки, назначение администраторов и розыгрыши.</p>
 */
@Component
public class ChatAdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAuthGuard guard;

    public ChatAdminAuthInterceptor(AdminAuthGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        return guard.allowChatAdmin(request, response);
    }
}
