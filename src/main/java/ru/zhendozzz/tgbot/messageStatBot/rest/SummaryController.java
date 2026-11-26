package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.SummaryService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Ручной запуск саммари за прошедший день (кнопка «Сделать сейчас» в настройках чата).
 * Доступ — только супер-админ: запуск тратит токены DeepSeek.
 */
@Controller
@RequestMapping("/app/summary")
public class SummaryController {

    private final SummaryService summaryService;
    private final CurrentUserService currentUserService;

    public SummaryController(SummaryService summaryService, CurrentUserService currentUserService) {
        this.summaryService = summaryService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{chatId}")
    public String runNow(@PathVariable String chatId,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        if (!currentUserService.isSuperAdmin(request)) {
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Доступ только для супер-админа");
            return null;
        }
        boolean sent = false;
        try {
            sent = summaryService.summarizeChat(chatId, LocalDate.now().minusDays(1));
        } catch (Exception e) {
            // Причина уже в логах сервиса; админу показываем «не отправлено».
        }
        return "redirect:/app/chat-settings/" + chatId + "?summary=" + (sent ? "sent" : "skipped");
    }
}
