package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.PrivateChatService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.util.List;

/**
 * Личные сообщения боту: список переписок и чтение одной.
 *
 * <p>Доступ — только супер-админ, проверяется {@code SuperAdminAuthInterceptor}
 * (пути {@code /app/dms} и {@code /app/dm/**} в {@code WebMvcConfig}).</p>
 */
@Controller
@RequestMapping("/app")
public class PrivateChatController {

    private final PrivateChatService privateChatService;
    private final CurrentUserService currentUserService;

    public PrivateChatController(PrivateChatService privateChatService, CurrentUserService currentUserService) {
        this.privateChatService = privateChatService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dms")
    public String chats(HttpServletRequest request, HttpServletResponse response, Model model) throws IOException {
        if (!isSuperAdmin(request, response)) {
            return null;
        }
        model.addAttribute("chats", privateChatService.findChats());
        return "dms";
    }

    @GetMapping("/dm/{chatId}")
    public String chat(@PathVariable String chatId,
                       @QueryParam("page") String page,
                       @QueryParam("count") String count,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       Model model) throws IOException {
        if (!isSuperAdmin(request, response)) {
            return null;
        }
        String id = privateChatService.normalizeChatId(chatId);
        if (id == null) {
            // Личный чат — только положительный числовой id; группы и мусор в URL не пускаем.
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Личный чат не найден");
            return null;
        }
        int currentPage = MiniAppController.parsePage(page);
        int cnt = MiniAppController.parseCount(count);
        List<ChatMessageDto> messages = privateChatService.findMessages(id, currentPage, cnt);
        model.addAttribute("messages", messages);
        model.addAttribute("chatId", id);
        model.addAttribute("chatTitle", privateChatService.findTitle(id));
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("count", cnt);
        model.addAttribute("prevPage", Math.max(currentPage - 1, 0));
        model.addAttribute("nextPage", currentPage + 1);
        return "dm";
    }

    /**
     * Перепроверка прав внутри метода: интерсептор закрывает пути в {@code WebMvcConfig},
     * но страница не должна открыться, даже если маршрут добавят в обход него.
     */
    private boolean isSuperAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (currentUserService.isSuperAdmin(request)) {
            return true;
        }
        response.setHeader("Cache-Control", "no-store");
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Доступ только для супер-админа");
        return false;
    }
}
