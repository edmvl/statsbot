package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAdminServiceTest {

    private static final long CHAT_ID = -100500L;
    private static final long USER_ID = 777L;

    private SettingsService settingsService;
    private MessageExecutor messageExecutor;
    private ChatAdminService service;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        messageExecutor = mock(MessageExecutor.class);
        service = new ChatAdminService(settingsService, messageExecutor);
        when(settingsService.isUserAdmin(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void superAdminIsAlwaysAdmin() {
        assertThat(service.isAdmin(CHAT_ID, AccessConstants.SUPER_ADMIN_ID)).isTrue();
        verify(messageExecutor, never()).getUserStatusInChat(anyLong(), anyLong());
    }

    @Test
    void botAdminFromDatabaseNeedsNoTelegramCall() {
        when(settingsService.isUserAdmin(CHAT_ID, USER_ID)).thenReturn(true);

        assertThat(service.isAdmin(CHAT_ID, USER_ID)).isTrue();
        verify(messageExecutor, never()).getUserStatusInChat(anyLong(), anyLong());
    }

    @Test
    void liveAdminBecomesBotAdminImmediately() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("administrator");

        assertThat(service.isAdmin(CHAT_ID, USER_ID)).isTrue();
        verify(settingsService).setAdmin(String.valueOf(CHAT_ID), String.valueOf(USER_ID), true);
    }

    @Test
    void creatorIsAdminToo() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("creator");

        assertThat(service.isAdmin(CHAT_ID, USER_ID)).isTrue();
        verify(settingsService).setAdmin(String.valueOf(CHAT_ID), String.valueOf(USER_ID), true);
    }

    @Test
    void ordinaryMemberIsNotAdminAndNothingIsSaved() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("member");

        assertThat(service.isAdmin(CHAT_ID, USER_ID)).isFalse();
        verify(settingsService, never()).setAdmin(anyString(), anyString(), eq(true));
    }

    @Test
    void negativeStatusIsNotCached() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("member");

        service.isAdmin(CHAT_ID, USER_ID);
        service.isAdmin(CHAT_ID, USER_ID);

        // Негатив не кэшируем: назначили админом — со следующей команды уже админ.
        verify(messageExecutor, times(2)).getUserStatusInChat(CHAT_ID, USER_ID);
    }

    @Test
    void adminStatusIsCachedForTenMinutes() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("administrator");

        service.chatStatus(CHAT_ID, USER_ID);
        service.chatStatus(CHAT_ID, USER_ID);

        verify(messageExecutor, times(1)).getUserStatusInChat(CHAT_ID, USER_ID);
    }
}
