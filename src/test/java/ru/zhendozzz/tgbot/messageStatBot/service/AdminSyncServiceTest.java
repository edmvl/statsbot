package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSyncServiceTest {

    private static final long CHAT_ID = -100500L;
    private static final long USER_ID = 777L;

    private SettingsService settingsService;
    private MessageExecutor messageExecutor;
    private AdminSyncService service;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        messageExecutor = mock(MessageExecutor.class);
        service = new AdminSyncService(settingsService, messageExecutor);
        when(settingsService.isUserAdmin(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void makesLiveChatAdminBotAdmin() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("administrator");

        service.syncIfNeeded(CHAT_ID, USER_ID);

        verify(settingsService).setAdmin(String.valueOf(CHAT_ID), String.valueOf(USER_ID), true);
    }

    @Test
    void recognisesCreator() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("creator");

        service.syncIfNeeded(CHAT_ID, USER_ID);

        verify(settingsService).setAdmin(String.valueOf(CHAT_ID), String.valueOf(USER_ID), true);
    }

    @Test
    void ignoresOrdinaryMembers() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("member");

        service.syncIfNeeded(CHAT_ID, USER_ID);

        verify(settingsService, never()).setAdmin(anyString(), anyString(), eq(true));
    }

    @Test
    void checksLiveStatusOnlyOnceAnHour() {
        when(messageExecutor.getUserStatusInChat(CHAT_ID, USER_ID)).thenReturn("member");

        service.syncIfNeeded(CHAT_ID, USER_ID);
        service.syncIfNeeded(CHAT_ID, USER_ID);

        verify(messageExecutor, times(1)).getUserStatusInChat(CHAT_ID, USER_ID);
    }

    @Test
    void skipsUserWhoIsAlreadyBotAdmin() {
        when(settingsService.isUserAdmin(CHAT_ID, USER_ID)).thenReturn(true);

        service.syncIfNeeded(CHAT_ID, USER_ID);

        verify(messageExecutor, never()).getUserStatusInChat(anyLong(), anyLong());
    }

    @Test
    void skipsSuperAdmin() {
        service.syncIfNeeded(CHAT_ID, AccessConstants.SUPER_ADMIN_ID);

        verify(messageExecutor, never()).getUserStatusInChat(anyLong(), anyLong());
    }
}
