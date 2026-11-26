package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.SettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private final ChatSettingsRepo chatSettingsRepo = mock(ChatSettingsRepo.class);
    private final SettingsRepo settingsRepo = mock(SettingsRepo.class);
    private final SettingsService service = new SettingsService(chatSettingsRepo, settingsRepo);

    @Test
    void unavailableChatDisablesEveryMailing() {
        when(chatSettingsRepo.findFirstByChatIdAndSettingName("-100", "chat_unavailable"))
                .thenReturn(setting("-100", "chat_unavailable", "true"));
        when(chatSettingsRepo.findFirstByChatIdAndSettingName("-100", "enable_reminder"))
                .thenReturn(setting("-100", "enable_reminder", "true"));

        assertThat(service.isUnavailable("-100")).isTrue();
        assertThat(service.isEnabled("-100", ChatSettingConstants.ENABLE_REMINDER)).isFalse();
    }

    @Test
    void markUnavailableStoresFlag() {
        when(chatSettingsRepo.findFirstByChatIdAndSettingName("-100", "chat_unavailable")).thenReturn(null);

        service.markUnavailable("-100");

        ArgumentCaptor<ChatSettings> captor = ArgumentCaptor.forClass(ChatSettings.class);
        verify(chatSettingsRepo).save(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo("-100");
        assertThat(captor.getValue().getSettingName()).isEqualTo("chat_unavailable");
        assertThat(captor.getValue().getSettingValue()).isEqualTo("true");
    }

    @Test
    void markUnavailableIsIdempotent() {
        when(chatSettingsRepo.findFirstByChatIdAndSettingName("-100", "chat_unavailable"))
                .thenReturn(setting("-100", "chat_unavailable", "true"));

        service.markUnavailable("-100");

        verify(chatSettingsRepo, never()).save(any(ChatSettings.class));
    }

    @Test
    void markAvailableRemovesFlag() {
        ChatSettings flag = setting("-100", "chat_unavailable", "true");
        when(chatSettingsRepo.findFirstByChatIdAndSettingName("-100", "chat_unavailable")).thenReturn(flag);

        service.markAvailable("-100");

        verify(chatSettingsRepo).delete(flag);
    }

    @Test
    void enabledChatsSkipUnavailableOnes() {
        when(chatSettingsRepo.findBySettingName("enable_reminder")).thenReturn(List.of(
                setting("-100", "enable_reminder", "true"),
                setting("-200", "enable_reminder", "true"),
                setting("-300", "enable_reminder", "false")));
        when(chatSettingsRepo.findBySettingName("chat_unavailable")).thenReturn(List.of(
                setting("-200", "chat_unavailable", "true")));

        assertThat(service.findEnabledBySettingName(ChatSettingConstants.ENABLE_REMINDER))
                .containsExactly("-100");
    }

    private static ChatSettings setting(String chatId, String name, String value) {
        ChatSettings settings = new ChatSettings();
        settings.setChatId(chatId);
        settings.setSettingName(name);
        settings.setSettingValue(value);
        return settings;
    }
}
