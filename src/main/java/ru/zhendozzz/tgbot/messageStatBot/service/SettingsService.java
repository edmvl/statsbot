package ru.zhendozzz.tgbot.messageStatBot.service;

import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.entity.Settings;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.SettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SettingsService {

    private final ChatSettingsRepo chatSettingsRepo;
    private final SettingsRepo settingsRepo;

    public SettingsService(ChatSettingsRepo chatSettingsRepo, SettingsRepo settingsRepo) {
        this.chatSettingsRepo = chatSettingsRepo;
        this.settingsRepo = settingsRepo;
    }

    public boolean isEnabled(String chatId, ChatSettingConstants name) {
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, name.getDbValue());
        return Objects.nonNull(setting) && "true".equals(setting.getSettingValue());
    }

    public boolean isEnabled(Long chatId, ChatSettingConstants name) {
        return isEnabled(String.valueOf(chatId), name);
    }

    public boolean isDisabled(String chatId, ChatSettingConstants name) {
        return !isEnabled(chatId, name);
    }
    public boolean isDisabled(Long chatId, ChatSettingConstants name) {
        return !isEnabled(String.valueOf(chatId), name);
    }

    public String findTripChannelId() {
        ChatSettings setting = chatSettingsRepo.findFirstBySettingNameAndSettingValueOrderByIdAsc(
                ChatSettingConstants.TRIP_CHANNEL.getDbValue(), "true");
        return Objects.nonNull(setting) ? setting.getChatId() : null;
    }

    public boolean isUserAdmin(Long chatId, Long userId) {
        Settings settings = settingsRepo.findByChatIdAndUserId(String.valueOf(chatId), String.valueOf(userId));
        return Objects.nonNull(settings) && settings.getIsAdmin();
    }

    public List<Settings> findAdmins(String chatId) {
        return settingsRepo.findByChatIdAndIsAdminIsTrue(chatId);
    }

    public void setAdmin(String chatId, String userId, boolean isAdmin) {
        Settings settings = settingsRepo.findByChatIdAndUserId(chatId, userId);
        if (Objects.isNull(settings)) {
            settings = new Settings();
            settings.setChatId(chatId);
            settings.setUserId(userId);
        }
        settings.setIsAdmin(isAdmin);
        settingsRepo.save(settings);
    }

    public List<String> findEnableReminderChats() {
        return findEnabledBySettingName(ChatSettingConstants.ENABLE_REMINDER);
    }

    public List<String> findEnabledBySettingName(ChatSettingConstants constant) {
        return chatSettingsRepo.findBySettingName(constant.getDbValue()).stream()
                .filter(chatSettings -> "true".equals(chatSettings.getSettingValue()))
                .map(ChatSettings::getChatId)
                .collect(Collectors.toList());
    }

}
