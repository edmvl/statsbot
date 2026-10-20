package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.FileDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

@Service
@lombok.extern.java.Log
public class LogService implements NeedProcessMessage {

    private final LogRepo logRepo;

    public LogService(LogRepo logRepo) {
        this.logRepo = logRepo;
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

    public void save(MessageDTO messageDTO) {
        log.log(Level.INFO,
                String.format("%s %s %s", messageDTO.getChatName(), messageDTO.getUserName(), messageDTO.getUserText()));
        Log log = new Log();
        log.setChatId(String.valueOf(messageDTO.getChatId()));
        log.setChatName(messageDTO.getChatName());
        log.setUserId(String.valueOf(messageDTO.getUserId()));
        log.setDateTime(messageDTO.getDateTime());
        log.setText(messageDTO.getUserText());
        log.setLoggerType(messageDTO.isServiceMessage() ? "service" : "chatbot");
        FileDto file = messageDTO.getFile();
        if (Objects.nonNull(file)) {
            log.setFileType(file.getFileType().getFileType());
            log.setFile(file.getFileId());
        }
        logRepo.save(log);
    }


    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        save(messageDTO);
    }
}
