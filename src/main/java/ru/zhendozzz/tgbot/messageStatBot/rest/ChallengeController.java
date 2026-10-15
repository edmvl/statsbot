package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.zhendozzz.tgbot.messageStatBot.service.ChallengeService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Запуск розыгрыша из мини-аппа (кнопка на странице списка чатов).
 */
@Controller
@RequestMapping("/app/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping("/start")
    public String start(
            @RequestParam("chatId") String chatId,
            @RequestParam("chatName") String chatName,
            @RequestParam("description") String description,
            @RequestParam("endDate") String endDate,
            @RequestParam("endTime") String endTime
    ) {
        try {
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.parse(endTime));
            challengeService.start(null, chatId, chatName, LocalDateTime.now(), end, description);
        } catch (Exception ignored) {
            // некорректные дата/время — просто возвращаемся к списку чатов
        }
        return "redirect:/app/chats";
    }
}
