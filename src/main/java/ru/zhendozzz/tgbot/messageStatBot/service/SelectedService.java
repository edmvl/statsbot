package ru.zhendozzz.tgbot.messageStatBot.service;

import ru.zhendozzz.tgbot.messageStatBot.entity.Selected;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.SelectedRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class SelectedService {

    private final SelectedRepo stinkyRepo;
    private final LogRepo logRepo;
    private final StatsService statsService;
    private final SettingsService settingsService;
    private final MessageExecutor messageExecutor;

    public SelectedService(SelectedRepo stinkyRepo, LogRepo logRepo, StatsService statsService, SettingsService settingsService, MessageExecutor messageExecutor) {
        this.stinkyRepo = stinkyRepo;
        this.logRepo = logRepo;
        this.statsService = statsService;
        this.settingsService = settingsService;
        this.messageExecutor = messageExecutor;
    }

    public Selected findByMessage(String chatId, LocalDate date) {
        return stinkyRepo.findByChatIdAndDate(chatId, date);
    }

    public void save(String chatId, String userId, LocalDate date) {
        Selected selected = new Selected();
        selected.setChatId(chatId);
        selected.setUserId(userId);
        selected.setDate(date);
        stinkyRepo.save(selected);
    }

    public String getStinky(String chatId) {
        List<String> distinctUserIdByChatId = logRepo.findDistinctUserIdByChatId(chatId);
        int i = (int) (Math.random() * distinctUserIdByChatId.size());
        return distinctUserIdByChatId.get(i);
    }

    public void sendSelected(Long chatId, Integer messageId) {
        sendSelected(chatId);
        messageExecutor.deleteMessage(chatId, messageId);
    }

    public void sendSelectedAllChat() {
        List<String> chatIds = statsService.findAllChats();
        for (String chatId : chatIds) {
            if (settingsService.isDisabled(chatId, ChatSettingConstants.ENABLE_SELECTED)) {
                continue;
            }
            try {
                sendSelected(Long.valueOf(chatId));
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    public void sendSelected(Long chatId) {
        Selected existedSelected = findByMessage(chatId.toString(), LocalDate.now());
        SendMessage sendMessage = new SendMessage();
        String text;
        User user;
        String stinkyUserId;
        if (Objects.nonNull(existedSelected)) {
            text = "Сегодня нам проставляется:\n";
            stinkyUserId = existedSelected.getUserId();
            String userNames = String.join(",", logRepo.findUserNameByUserId(stinkyUserId));
            user = new User(Long.valueOf(stinkyUserId), userNames, false);
        } else {
            text = "Поиск спонсора нашей пьянки... Сегодня нам проставляется:\n";
            stinkyUserId = getStinky(chatId.toString());
            String userNames = String.join(",", logRepo.findUserNameByUserId(stinkyUserId));
            user = new User(Long.valueOf(stinkyUserId), userNames, false);
            save(chatId.toString(), stinkyUserId, LocalDate.now());
        }
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setUser(user);
        messageEntity.setOffset(text.length());
        String userIdentityText = firstName + " " + (Objects.nonNull(lastName) ? lastName : "") + "\n";
        text += userIdentityText;
        messageEntity.setLength(userIdentityText.length());
        messageEntity.setType("text_mention");
        sendMessage.setEntities(List.of(messageEntity));
        sendMessage.setText(text);
        sendMessage.setChatId(chatId);
        messageExecutor.sendMessage(sendMessage);
    }
}
