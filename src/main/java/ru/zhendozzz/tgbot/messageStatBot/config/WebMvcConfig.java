package ru.zhendozzz.tgbot.messageStatBot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SuperAdminAuthInterceptor superAdminAuthInterceptor;
    private final ChatAdminAuthInterceptor chatAdminAuthInterceptor;

    public WebMvcConfig(
            SuperAdminAuthInterceptor superAdminAuthInterceptor,
            ChatAdminAuthInterceptor chatAdminAuthInterceptor
    ) {
        this.superAdminAuthInterceptor = superAdminAuthInterceptor;
        this.chatAdminAuthInterceptor = chatAdminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Страницы конкретного чата: супер-админ или администратор этого чата.
        registry.addInterceptor(chatAdminAuthInterceptor)
                .addPathPatterns("/app/chats", "/app/chats/**", "/app/chat/**",
                        "/app/chat-settings/**", "/app/chat-admins/**", "/app/challenges/**");

        // Общие админские разделы остаются только для супер-админа.
        registry.addInterceptor(superAdminAuthInterceptor)
                .addPathPatterns("/app/subscriptions", "/app/ads",
                        "/api/ads", "/api/ads/**");
    }
}
