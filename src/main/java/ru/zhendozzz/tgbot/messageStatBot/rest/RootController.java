package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Корень сайта.
 *
 * <p>Приложение живёт под {@code /app}. Без этого маппинга Spring Boot отдаёт
 * {@code templates/index.html} как welcome-page с пустой моделью, и шаблон падает
 * на выражениях вида {@code !adminAccess} (SpEL не умеет {@code !null}).</p>
 */
@Controller
public class RootController {

    @GetMapping("/")
    public String root() {
        return "redirect:/app";
    }
}
