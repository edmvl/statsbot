package ru.zhendozzz.tgbot.messageStatBot.service;

import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.entity.Settings;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.SettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        if (name != ChatSettingConstants.CHAT_UNAVAILABLE && isUnavailable(chatId)) {
            return false;
        }
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, name.getDbValue());
        return Objects.nonNull(setting) && "true".equals(setting.getSettingValue());
    }

    /** Чат недоступен: Telegram отвечал «chat not found», рассылки в него остановлены. */
    public boolean isUnavailable(String chatId) {
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(
                chatId, ChatSettingConstants.CHAT_UNAVAILABLE.getDbValue());
        return Objects.nonNull(setting) && "true".equals(setting.getSettingValue());
    }

    /**
     * Помечает чат недоступным, чтобы ночные рассылки не долбились в удалённый чат.
     * Идемпотентно: повторная отметка не создаёт дубль.
     */
    public void markUnavailable(String chatId) {
        if (Objects.isNull(chatId) || chatId.isBlank() || isUnavailable(chatId)) {
            return;
        }
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(
                chatId, ChatSettingConstants.CHAT_UNAVAILABLE.getDbValue());
        if (Objects.isNull(setting)) {
            setting = new ChatSettings();
            setting.setChatId(chatId);
            setting.setSettingName(ChatSettingConstants.CHAT_UNAVAILABLE.getDbValue());
        }
        setting.setSettingValue("true");
        chatSettingsRepo.save(setting);
    }

    /** Снимает отметку: бот снова получает сообщения из чата (его вернули или разблокировали). */
    public void markAvailable(String chatId) {
        if (Objects.isNull(chatId) || chatId.isBlank()) {
            return;
        }
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(
                chatId, ChatSettingConstants.CHAT_UNAVAILABLE.getDbValue());
        if (Objects.nonNull(setting)) {
            chatSettingsRepo.delete(setting);
        }
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
        return isUserAdmin(String.valueOf(chatId), userId);
    }

    public boolean isUserAdmin(String chatId, Long userId) {
        if (chatId == null || userId == null) {
            return false;
        }
        Settings settings = settingsRepo.findByChatIdAndUserId(chatId, String.valueOf(userId));
        return Objects.nonNull(settings) && Boolean.TRUE.equals(settings.getIsAdmin());
    }

    /** Чаты, в которых пользователь назначен администратором бота. */
    public List<String> findAdminChatIds(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return settingsRepo.findByUserIdAndIsAdminIsTrue(String.valueOf(userId)).stream()
                .map(Settings::getChatId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /** true — пользователь админ хотя бы одного чата. */
    public boolean isAdminOfAnyChat(Long userId) {
        return !findAdminChatIds(userId).isEmpty();
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
        Set<String> unavailable = chatSettingsRepo.findBySettingName(ChatSettingConstants.CHAT_UNAVAILABLE.getDbValue())
                .stream()
                .filter(chatSettings -> "true".equals(chatSettings.getSettingValue()))
                .map(ChatSettings::getChatId)
                .collect(Collectors.toSet());
        return chatSettingsRepo.findBySettingName(constant.getDbValue()).stream()
                .filter(chatSettings -> "true".equals(chatSettings.getSettingValue()))
                .map(ChatSettings::getChatId)
                .filter(chatId -> !unavailable.contains(chatId))
                .collect(Collectors.toList());
    }

}
