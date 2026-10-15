package ru.zhendozzz.tgbot.messageStatBot.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Защита админских страниц бота: пускает только супер-админа.
 */
@Component
public class SuperAdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAuthGuard guard;

    public SuperAdminAuthInterceptor(AdminAuthGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        return guard.allow(request, response, false);
    }
}
