package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import ru.zhendozzz.tgbot.messageStatBot.entity.Selected;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.SelectedRepo;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelectedServiceTest {

    private static final String CHAT_ID = "-100";
    private static final Long CHAT = -100L;

    private final SelectedRepo selectedRepo = mock(SelectedRepo.class);
    private final LogRepo logRepo = mock(LogRepo.class);
    private final StatsService statsService = mock(StatsService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);

    private final SelectedService service =
            new SelectedService(selectedRepo, logRepo, statsService, settingsService, messageExecutor);

    private void givenNoSelectionToday() {
        when(selectedRepo.findByChatIdAndDate(eq(CHAT_ID), any(LocalDate.class))).thenReturn(null);
        when(logRepo.findUserNameByUserId(anyString())).thenReturn(List.of("ivan"));
    }

    @Test
    void savesSelectionOnlyAfterSuccessfulSend() {
        givenNoSelectionToday();
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of("42"));
        when(messageExecutor.sendMessage(any(SendMessage.class))).thenReturn(777);

        service.sendSelected(CHAT);

        InOrder order = inOrder(messageExecutor, selectedRepo);
        order.verify(messageExecutor).sendMessage(any(SendMessage.class));
        order.verify(selectedRepo).save(any(Selected.class));
        verify(selectedRepo, times(1)).save(any(Selected.class));
    }

    @Test
    void doesNotSaveWhenTelegramRejectsAllAttempts() {
        givenNoSelectionToday();
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of("42"));
        when(messageExecutor.sendMessage(any(SendMessage.class))).thenReturn(null);

        service.sendSelected(CHAT);

        verify(messageExecutor, times(3)).sendMessage(any(SendMessage.class));
        verify(selectedRepo, never()).save(any(Selected.class));
    }

    @Test
    void savesOnceAfterRetrySucceeds() {
        givenNoSelectionToday();
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of("42"));
        when(messageExecutor.sendMessage(any(SendMessage.class))).thenReturn(null, null, 501);

        service.sendSelected(CHAT);

        verify(messageExecutor, times(3)).sendMessage(any(SendMessage.class));
        InOrder order = inOrder(messageExecutor, selectedRepo);
        order.verify(messageExecutor, times(3)).sendMessage(any(SendMessage.class));
        order.verify(selectedRepo, times(1)).save(any(Selected.class));
    }

    @Test
    void doesNotSaveWhenSendThrows() {
        givenNoSelectionToday();
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of("42"));
        when(messageExecutor.sendMessage(any(SendMessage.class))).thenThrow(new IllegalStateException("boom"));

        service.sendSelected(CHAT);

        verify(messageExecutor, times(3)).sendMessage(any(SendMessage.class));
        verify(selectedRepo, never()).save(any(Selected.class));
    }

    @Test
    void reusesTodaysSelectionWithoutNewDraw() {
        Selected saved = new Selected();
        saved.setChatId(CHAT_ID);
        saved.setUserId("99");
        when(selectedRepo.findByChatIdAndDate(eq(CHAT_ID), any(LocalDate.class))).thenReturn(saved);
        when(logRepo.findUserNameByUserId("99")).thenReturn(List.of("petya"));
        when(messageExecutor.sendMessage(any(SendMessage.class))).thenReturn(777);

        service.sendSelected(CHAT);

        verify(logRepo, never()).findDistinctUserIdByChatIdSince(anyString(), any());
        verify(selectedRepo, never()).save(any(Selected.class));
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(messageExecutor).sendMessage(sent.capture());
        assertEquals(99L, sent.getValue().getEntities().get(0).getUser().getId());
        assertTrue(sent.getValue().getText().startsWith("Сегодня нам проставляется"));
    }

    @Test
    void drawsAmongActiveParticipants() {
        givenNoSelectionToday();
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of("7"));
        when(messageExecutor.sendMessage(any(SendMessage.class))).thenReturn(777);

        assertEquals("7", service.getStinky(CHAT_ID));
        verify(logRepo, never()).findDistinctUserIdByChatId(anyString());
    }

    @Test
    void fallsBackToAllParticipantsWhenNobodyWasActive() {
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of());
        when(logRepo.findDistinctUserIdByChatId(CHAT_ID)).thenReturn(List.of("8"));

        assertEquals("8", service.getStinky(CHAT_ID));
    }

    @Test
    void failsLoudlyWhenChatHasNoParticipantsAtAll() {
        when(logRepo.findDistinctUserIdByChatIdSince(eq(CHAT_ID), any())).thenReturn(List.of());
        when(logRepo.findDistinctUserIdByChatId(CHAT_ID)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.getStinky(CHAT_ID));
    }
}
