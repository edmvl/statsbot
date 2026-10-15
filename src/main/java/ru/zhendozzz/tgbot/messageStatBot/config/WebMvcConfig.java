package ru.zhendozzz.tgbot.messageStatBot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SuperAdminAuthInterceptor superAdminAuthInterceptor;

    public WebMvcConfig(SuperAdminAuthInterceptor superAdminAuthInterceptor) {
        this.superAdminAuthInterceptor = superAdminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(superAdminAuthInterceptor)
                .addPathPatterns("/app/chat-settings/**", "/app/chat-admins/**", "/app/chats", "/app/chats/**",
                        "/app/chat/**", "/app/challenges/**", "/app/subscriptions", "/app/ads",
                        "/api/ads", "/api/ads/**");
    }
}
