package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.entity.LogEdit;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogEditRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogServiceTest {

    private final LogRepo logRepo = mock(LogRepo.class);
    private final LogEditRepo logEditRepo = mock(LogEditRepo.class);
    private final LogService service = new LogService(logRepo, logEditRepo);

    @Test
    void savesNewMessageWithTelegramId() {
        service.save(dto(false));

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepo).save(captor.capture());
        Log saved = captor.getValue();
        assertThat(saved.getMessageId()).isEqualTo(10);
        assertThat(saved.getText()).isEqualTo("новый текст");
        assertThat(saved.getDeleted()).isFalse();
        assertThat(saved.getEditedAt()).isNull();
        verify(logEditRepo, never()).save(any(LogEdit.class));
    }

    @Test
    void editStoresPreviousVersionAndKeepsLatest() {
        Log existing = log(1L, "старый текст");
        when(logRepo.findFirstByChatIdAndMessageId("-100", 10)).thenReturn(Optional.of(existing));

        service.updateEdited(dto(true));

        ArgumentCaptor<LogEdit> captor = ArgumentCaptor.forClass(LogEdit.class);
        verify(logEditRepo).save(captor.capture());
        LogEdit previous = captor.getValue();
        assertThat(previous.getLogId()).isEqualTo(1L);
        assertThat(previous.getText()).isEqualTo("старый текст");
        assertThat(previous.getEditedAt()).isNotNull();

        // В основной таблице — последняя версия.
        assertThat(existing.getText()).isEqualTo("новый текст");
        assertThat(existing.getEditedAt()).isNotNull();
        verify(logRepo).save(existing);
    }

    @Test
    void editOfUnknownMessageIsSavedAsNew() {
        when(logRepo.findFirstByChatIdAndMessageId("-100", 10)).thenReturn(Optional.empty());

        service.updateEdited(dto(true));

        // Старой версии нет (сообщение появилось до учёта правок) — просто сохраняем.
        verify(logRepo).save(any(Log.class));
        verify(logEditRepo, never()).save(any(LogEdit.class));
    }

    @Test
    void markDeletedSetsFlag() {
        Log existing = log(1L, "текст");
        when(logRepo.findFirstByChatIdAndMessageId("-100", 10)).thenReturn(Optional.of(existing));

        service.markDeleted(-100L, 10);

        assertThat(existing.getDeleted()).isTrue();
        verify(logRepo).save(existing);
    }

    @Test
    void markDeletedIgnoresUnknownMessage() {
        when(logRepo.findFirstByChatIdAndMessageId("-100", 10)).thenReturn(Optional.empty());

        service.markDeleted(-100L, 10);

        verify(logRepo, never()).save(any(Log.class));
    }

    @Test
    void editsAreGroupedByMessageRow() {
        LogEdit first = edit(1L, "версия 1");
        LogEdit second = edit(1L, "версия 2");
        LogEdit third = edit(2L, "версия 3");
        when(logEditRepo.findByLogIdInOrderByIdAsc(List.of(1L, 2L)))
                .thenReturn(List.of(first, second, third));

        Map<Long, List<LogEdit>> edits = service.findEdits(List.of(1L, 2L));

        assertThat(edits.get(1L)).containsExactly(first, second);
        assertThat(edits.get(2L)).containsExactly(third);
    }

    @Test
    void editsForEmptyPageAreEmpty() {
        assertThat(service.findEdits(List.of())).isEmpty();
        assertThat(service.findEdits(null)).isEmpty();
        verify(logEditRepo, never()).findByLogIdInOrderByIdAsc(any());
    }

    private MessageDTO dto(boolean edited) {
        return MessageDTO.builder()
                .chatId(-100L)
                .chatName("Чат")
                .messageId(10)
                .dateTime(LocalDateTime.of(2026, 10, 22, 12, 0))
                .userId(777L)
                .userName("ivan")
                .userText("новый текст")
                .edited(edited)
                .editDateTime(edited ? LocalDateTime.of(2026, 10, 22, 12, 5) : null)
                .build();
    }

    private Log log(Long id, String text) {
        Log log = new Log();
        log.setId(id);
        log.setChatId("-100");
        log.setMessageId(10);
        log.setText(text);
        log.setDeleted(false);
        return log;
    }

    private LogEdit edit(Long logId, String text) {
        LogEdit edit = new LogEdit();
        edit.setLogId(logId);
        edit.setText(text);
        edit.setEditedAt(LocalDateTime.now());
        return edit;
    }
}
