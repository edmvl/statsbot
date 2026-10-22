package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.FileDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.entity.LogEdit;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogEditRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.logging.Level;

@Service
@lombok.extern.java.Log
public class LogService implements NeedProcessMessage {

    private final LogRepo logRepo;
    private final LogEditRepo logEditRepo;

    public LogService(LogRepo logRepo, LogEditRepo logEditRepo) {
        this.logRepo = logRepo;
        this.logEditRepo = logEditRepo;
    }

    public List<ChatMessageDto> findByChatId(String chatId, int page, int size) {
        return logRepo.getMessagesByChatId(chatId, PageRequest.of(page, size)).getContent();
    }

    public String findChatName(String chatId) {
        return logRepo.findChatNameByChatId(chatId);
    }

    public List<ChatViewDto> getAllChats() {
        return logRepo.getAllChats();
    }

    /** Предыдущие версии сообщений страницы: id строки лога → версии по порядку. */
    public Map<Long, List<LogEdit>> findEdits(Collection<Long> logIds) {
        if (Objects.isNull(logIds) || logIds.isEmpty()) {
            return Map.of();
        }
        return logEditRepo.findByLogIdInOrderByIdAsc(logIds).stream()
                .collect(Collectors.groupingBy(LogEdit::getLogId, LinkedHashMap::new, Collectors.toList()));
    }

    public void save(MessageDTO messageDTO) {
        log.log(Level.INFO,
                String.format("%s %s %s", messageDTO.getChatName(), messageDTO.getUserName(), messageDTO.getUserText()));
        Log log = new Log();
        log.setChatId(String.valueOf(messageDTO.getChatId()));
        log.setChatName(messageDTO.getChatName());
        log.setUserId(String.valueOf(messageDTO.getUserId()));
        log.setDateTime(messageDTO.getDateTime());
        log.setMessageId(messageDTO.getMessageId());
        log.setDeleted(false);
        log.setLoggerType(messageDTO.isServiceMessage() ? "service" : "chatbot");
        applyContent(log, messageDTO);
        logRepo.save(log);
    }

    /**
     * Правка сообщения: текущую версию складываем в {@code log_edit}, в {@code log}
     * оставляем новую. Так в истории видно последнюю версию, а старые доступны
     * по кнопке «История изменений».
     */
    public void updateEdited(MessageDTO messageDTO) {
        String chatId = String.valueOf(messageDTO.getChatId());
        Log existing = logRepo.findFirstByChatIdAndMessageId(chatId, messageDTO.getMessageId()).orElse(null);
        if (Objects.isNull(existing)) {
            // Сообщение появилось раньше, чем мы начали хранить message_id:
            // старую версию взять неоткуда, сохраняем хотя бы актуальную.
            save(messageDTO);
            return;
        }
        LocalDateTime editedAt = Objects.nonNull(messageDTO.getEditDateTime())
                ? messageDTO.getEditDateTime()
                : LocalDateTime.now();

        LogEdit previous = new LogEdit();
        previous.setLogId(existing.getId());
        previous.setText(existing.getText());
        previous.setFileType(existing.getFileType());
        previous.setFile(existing.getFile());
        previous.setEditedAt(editedAt);
        logEditRepo.save(previous);

        applyContent(existing, messageDTO);
        existing.setEditedAt(editedAt);
        logRepo.save(existing);
    }

    /** Помечает сообщение удалённым (бот удалил его сам, например по фильтру слов). */
    public void markDeleted(Long chatId, Integer messageId) {
        if (Objects.isNull(chatId) || Objects.isNull(messageId)) {
            return;
        }
        logRepo.findFirstByChatIdAndMessageId(String.valueOf(chatId), messageId)
                .ifPresent(log -> {
                    log.setDeleted(true);
                    logRepo.save(log);
                });
    }

    private void applyContent(Log log, MessageDTO messageDTO) {
        log.setText(messageDTO.getUserText());
        log.setFileType(null);
        log.setFile(null);
        FileDto file = messageDTO.getFile();
        if (Objects.nonNull(file)) {
            log.setFileType(file.getFileType().getFileType());
            log.setFile(file.getFileId());
        }
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        if (messageDTO.isEdited()) {
            updateEdited(messageDTO);
            return;
        }
        save(messageDTO);
    }
}
