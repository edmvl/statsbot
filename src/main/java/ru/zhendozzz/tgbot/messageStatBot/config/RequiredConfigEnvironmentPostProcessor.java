package ru.zhendozzz.tgbot.messageStatBot.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * Проверяет, что конфигурация бота вообще задана, и падает с понятным сообщением.
 *
 * <p>Без этой проверки при пустом токене приложение падает где-то внутри
 * telegrambots с невнятным «Bot token and username can't be empty», и непонятно,
 * какой профиль активен и куда класть значения.</p>
 */
public class RequiredConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String token = value(environment, "telegram.bot.token");
        String username = value(environment, "telegram.bot.username");
        if (!token.isEmpty() && !username.isEmpty()) {
            return;
        }

        String profiles = environment.getActiveProfiles().length == 0
                ? "(не задан, используется весенний дефолт 'default')"
                : Arrays.toString(environment.getActiveProfiles());
        String message = System.lineSeparator()
                + "Не задан токен бота: телеграм-бот не сможет запуститься." + System.lineSeparator()
                + "Активный профиль: " + profiles + System.lineSeparator()
                + "Не хватает: " + (token.isEmpty() ? "telegram.bot.token " : "")
                + (username.isEmpty() ? "telegram.bot.username" : "") + System.lineSeparator()
                + "Задайте значения одним из способов:" + System.lineSeparator()
                + "  1) переменные окружения: TELEGRAM_BOT_TOKEN (и TELEGRAM_BOT_USERNAME);" + System.lineSeparator()
                + "  2) файл config/application-<профиль>.properties рядом с приложением" + System.lineSeparator()
                + "     (шаблоны: application-local.example.properties, application-prod.example.properties);" + System.lineSeparator()
                + "  3) указать профиль: SPRING_PROFILES_ACTIVE=prod." + System.lineSeparator();
        System.err.println(message);
        throw new IllegalStateException(message);
    }

    private String value(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        // значение может остаться неразрешённым плейсхолдером
        return trimmed.startsWith("${") ? "" : trimmed;
    }

    @Override
    public int getOrder() {
        // после ConfigDataEnvironmentPostProcessor, чтобы профильные файлы уже были прочитаны
        return Ordered.LOWEST_PRECEDENCE;
    }
}
