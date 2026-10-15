package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMemberViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Settings;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Controller
@RequestMapping("/app/chat-admins")
public class ChatAdminsController {

    /** Сколько участников показывать за раз (остальные — через поиск). */
    private static final int MEMBER_LIMIT = 100;

    private final LogRepo logRepo;
    private final UserRepo userRepo;
    private final SettingsService settingsService;

    public ChatAdminsController(LogRepo logRepo, UserRepo userRepo, SettingsService settingsService) {
        this.logRepo = logRepo;
        this.userRepo = userRepo;
        this.settingsService = settingsService;
    }

    @GetMapping("/{chatId}")
    public String getChatAdmins(
            @PathVariable String chatId,
            @RequestParam(value = "q", required = false) String q,
            Model model
    ) {
        String query = Objects.isNull(q) ? "" : q.trim();

        List<ChatAdminView> members = new ArrayList<>();
        Set<String> added = new HashSet<>();

        // Текущие администраторы — всегда сверху (чтобы их можно было снять с учёта).
        List<String> adminIds = settingsService.findAdmins(chatId).stream()
                .map(Settings::getUserId)
                .toList();
        if (!adminIds.isEmpty()) {
            userRepo.findMemberViewsByIds(adminIds).forEach(member -> {
                if (added.add(member.getUserId())) {
                    members.add(new ChatAdminView(member.getUserId(), displayName(member), member.getAvatarPath(), true));
                }
            });
            adminIds.forEach(id -> {
                if (added.add(id)) {
                    members.add(new ChatAdminView(id, id, null, true));
                }
            });
        }

        // Участники чата с серверным поиском и лимитом (одним запросом, без N+1).
        List<ChatMemberViewDto> found = logRepo.findChatMembers(chatId, query, MEMBER_LIMIT);
        found.forEach(member -> {
            if (added.add(member.getUserId())) {
                members.add(new ChatAdminView(member.getUserId(), displayName(member), member.getAvatarPath(), false));
            }
        });

        model.addAttribute("chatId", chatId);
        model.addAttribute("members", members);
        model.addAttribute("q", query);
        model.addAttribute("limitReached", found.size() >= MEMBER_LIMIT);
        return "chat-admins";
    }

    @PostMapping("/{chatId}")
    public String updateChatAdmins(
            @PathVariable String chatId,
            @RequestParam(value = "admin", required = false) List<String> adminIds,
            HttpServletResponse response
    ) throws IOException {
        Set<String> desired = Objects.isNull(adminIds) ? new HashSet<>() : new HashSet<>(adminIds);

        // Снимаем тех, кто больше не выбран (только текущих админов — их немного).
        settingsService.findAdmins(chatId).stream()
                .map(Settings::getUserId)
                .filter(id -> !desired.contains(id))
                .forEach(id -> settingsService.setAdmin(chatId, id, false));

        // Назначаем выбранных.
        desired.forEach(id -> settingsService.setAdmin(chatId, id, true));

        response.sendRedirect("/app/chat-admins/" + chatId);
        return "chat-admins";
    }

    private String displayName(ChatMemberViewDto member) {
        String name = Objects.isNull(member.getName()) ? "" : member.getName().trim();
        String userName = member.getUserName();
        String base = name.isEmpty()
                ? (Objects.nonNull(userName) && !userName.isEmpty() ? userName : member.getUserId())
                : name;
        if (Objects.nonNull(userName) && !userName.isEmpty() && !base.equals(userName)) {
            return base + " (" + userName + ")";
        }
        return base;
    }

    /**
     * DTO для отображения участника чата в шаблоне.
     */
    public static class ChatAdminView {
        private final String userId;
        private final String userName;
        private final String avatarPath;
        private final boolean admin;

        public ChatAdminView(String userId, String userName, String avatarPath, boolean admin) {
            this.userId = userId;
            this.userName = userName;
            this.avatarPath = avatarPath;
            this.admin = admin;
        }

        public String getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }

        public String getAvatarPath() {
            return avatarPath;
        }

        public boolean isAdmin() {
            return admin;
        }
    }
}
