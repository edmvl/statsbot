package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/app/chat-settings")
public class ChatSettingsController {

    private final ChatSettingsRepo chatSettingsRepo;

    public ChatSettingsController(ChatSettingsRepo chatSettingsRepo) {
        this.chatSettingsRepo = chatSettingsRepo;
    }

    @GetMapping("/{chatId}")
    public String getChatSettings(@PathVariable String chatId, Model model) {
        List<ChatSettingView> settings = new ArrayList<>();
        for (ChatSettingConstants constant : ChatSettingConstants.values()) {
            ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, constant.getDbValue());
            boolean enabled = Objects.nonNull(setting) && "true".equals(setting.getSettingValue());
            settings.add(new ChatSettingView(constant.getDbValue(), enabled));
        }
        model.addAttribute("chatId", chatId);
        model.addAttribute("settings", settings);
        return "chat-settings";
    }

    @PostMapping("/{chatId}")
    public String updateChatSettings(
            @PathVariable String chatId,
            @RequestParam(value = "setting", required = false) List<String> enabledSettings,
            HttpServletResponse response
    ) throws IOException {
        List<String> enabled = Objects.nonNull(enabledSettings) ? enabledSettings : new ArrayList<>();

        for (ChatSettingConstants constant : ChatSettingConstants.values()) {
            String settingName = constant.getDbValue();
            boolean shouldBeEnabled = enabled.contains(settingName);

            ChatSettings existing = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, settingName);
            if (Objects.isNull(existing)) {
                ChatSettings newSetting = new ChatSettings();
                newSetting.setChatId(chatId);
                newSetting.setSettingName(settingName);
                newSetting.setSettingValue(String.valueOf(shouldBeEnabled));
                chatSettingsRepo.save(newSetting);
            } else {
                existing.setSettingValue(String.valueOf(shouldBeEnabled));
                chatSettingsRepo.save(existing);
            }
        }

        response.sendRedirect("/app/chat-settings/" + chatId);
        return "chat-settings";
    }

    /**
     * DTO для отображения настройки в шаблоне.
     */
    public static class ChatSettingView {
        private final String name;
        private final boolean enabled;

        public ChatSettingView(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getLabel() {
            switch (name) {
                case "enable_selected": return "Спонсор дня";
                case "enable_holidays": return "Праздники";
                case "enable_stats": return "Статистика";
                case "enable_reminder": return "Напоминания о сезонах";
                case "trip_channel": return "Канал поездок";
                case "enable_member_control": return "Контроль участников";
                case "enable_ads": return "Показывать рекламу";
                default: return name;
            }
        }
    }
}
