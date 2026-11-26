package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.rest.ChatSettingsController.ChatSettingView;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.SummaryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSettingsControllerTest {

    private final ChatSettingsRepo chatSettingsRepo = mock(ChatSettingsRepo.class);
    private final SummaryService summaryService = mock(SummaryService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final ChatSettingsController controller =
            new ChatSettingsController(chatSettingsRepo, summaryService, currentUserService);

    @Test
    @SuppressWarnings("unchecked")
    void hidesInternalSettingsAndShowsSummaryToggle() {
        when(chatSettingsRepo.findFirstByChatIdAndSettingName(anyString(), anyString())).thenReturn(null);
        when(currentUserService.isSuperAdmin(any())).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.getChatSettings("-100", null, new MockHttpServletRequest(), model);

        List<ChatSettingView> settings = (List<ChatSettingView>) model.get("settings");
        assertThat(settings).extracting(ChatSettingView::getName)
                .doesNotContain("chat_unavailable")
                .contains("enable_summary");
        ChatSettingView summary = settings.stream()
                .filter(setting -> "enable_summary".equals(setting.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(summary.getLabel()).isEqualTo("Саммари за день");
        assertThat(model.get("superAdmin")).isEqualTo(false);
    }

    @Test
    void updateNeverWritesInternalFlag() throws Exception {
        when(chatSettingsRepo.findFirstByChatIdAndSettingName(anyString(), anyString())).thenReturn(null);

        controller.updateChatSettings("-100", List.of("enable_summary"), new MockHttpServletResponse());

        ArgumentCaptor<ChatSettings> saved = ArgumentCaptor.forClass(ChatSettings.class);
        verify(chatSettingsRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ChatSettings::getSettingName)
                .doesNotContain("chat_unavailable");
    }
}
