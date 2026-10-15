package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChattyDaysDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.StatsViewDto;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatsService implements CommandExecutable {

    private final LogRepo logRepo;
    private final MessageExecutor messageExecutor;

    private final SettingsService settingsService;

    public StatsService(
            LogRepo logRepo, MessageExecutor messageExecutor, SettingsService settingsService
    ) {
        this.logRepo = logRepo;
        this.messageExecutor = messageExecutor;
        this.settingsService = settingsService;
    }


    public List<StatsViewDto> getTop10ChattyUserId(Long chatId) {
        LocalDate startOfDay = LocalDate.now().minusDays(1);
        return getTopChattyUserId(chatId, startOfDay);
    }

    public List<StatsViewDto> getTopChattyUserId(Long chatId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endDate = date.atStartOfDay().withHour(23).withMinute(59).withSecond(59);
        return logRepo.findByChatIdAndDateBetweenOrderByCountDesc(chatId.toString(), startOfDay, endDate);
    }

    public List<StatsViewDto> getTopChattyWeek(Long chatId) {
        LocalDateTime startDate = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime endDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .atStartOfDay().withHour(23).withMinute(59).withSecond(59);
        return logRepo.findByChatIdAndDateBetweenOrderByCountDesc(chatId.toString(), startDate, endDate);
    }

    public List<String> findAllChats() {
        return logRepo.findDistinctChatId();
    }

    public void sendStatAllChat() {
        for (String chatId : findAllChats()) {
            if (settingsService.isDisabled(chatId, ChatSettingConstants.ENABLE_STATS)) {
                continue;
            }
            sendChatty(Long.valueOf(chatId));
        }

    }

    public void sendWeekStats(Long chatId) {
        List<StatsViewDto> top = getTopChattyWeek(chatId);
        StringBuilder text = new StringBuilder();
        text.append("Статистика за эту неделю\n");
        List<MessageEntity> messageEntities = getMessageEntities(top, text);
        sendMessage(chatId, messageEntities, text.toString());
    }

    private List<MessageEntity> getMessageEntities(List<StatsViewDto> top, StringBuilder text) {
        ArrayList<MessageEntity> messageEntities = new ArrayList<>();
        top.forEach(stats -> {
            String userNames = String.join(",", logRepo.findUserNameByUserId(stats.getUserId()));
            User user = new User(Long.valueOf(stats.getUserId()), userNames, false);
            messageEntities.add(getMessageEntity(text, stats.getCount(), user, userNames));
        });
        return messageEntities;
    }

    private void sendMessage(Long chatId, List<MessageEntity> messageEntities, String text) {
        SendMessage sendMessage = new SendMessage();
        if (messageEntities.isEmpty()) {
            sendMessage.setText("Что то сегодня молчат все...");
        } else {
            sendMessage.setEntities(messageEntities);
            sendMessage.setText(text);
        }
        sendMessage.setChatId(chatId);
        messageExecutor.sendMessage(sendMessage);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText(text);
        sendMessage.setChatId(chatId);
        messageExecutor.sendMessage(sendMessage);
    }

    private static MessageEntity getMessageEntity(
            StringBuilder text, Integer count, User user, String userNames
    ) {
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setOffset(text.length());
        String userIdentityText = userNames + "(" + count + ")" + "\n";
        text.append(userIdentityText);
        messageEntity.setLength(userIdentityText.length());
        messageEntity.setUser(user);
        messageEntity.setType("text_mention");
        return messageEntity;
    }

    public void sendChatty(Long chatId) {
        List<StatsViewDto> top = getTop10ChattyUserId(chatId);
        StringBuilder text = new StringBuilder();
        text.append("Топ 10 болтунов за день:\n");
        List<MessageEntity> messageEntities = getMessageEntities(top, text);
        sendMessage(chatId, messageEntities, text.toString());
    }

    public void sendChattyDays(Long chatId) {
        List<ChattyDaysDto> topChattyDays = logRepo.findTopChattyDays(chatId.toString());
        String message = topChattyDays.stream()
                .map(dto -> dto.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " (" + dto.getSm() + " сообщений)").collect(Collectors.joining("\n"));
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Топ активных дней\n" + message);
        messageExecutor.sendMessage(sendMessage);
    }

    public void sendWeekStats(Long chatId, Integer messageId) {
        sendWeekStats(chatId);
        messageExecutor.deleteMessage(chatId, messageId);
    }

    public void sendChatty(Long chatId, Integer messageId) {
        sendChatty(chatId);
        messageExecutor.deleteMessage(chatId, messageId);
    }

    public void sendChattyDays(Long chatId, Integer messageId) {
        sendChattyDays(chatId);
        messageExecutor.deleteMessage(chatId, messageId);
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.GET_WEEK_STATS.getCommand())) {
            sendWeekStats(messageDTO.getChatId(), messageDTO.getMessageId());
        }
        if (messageDTO.getUserText().startsWith(BotCommands.GET_CHATTY.getCommand())) {
            sendChatty(messageDTO.getChatId(), messageDTO.getMessageId());
        }
        if (messageDTO.getUserText().startsWith(BotCommands.GET_CHATTY_DAYS.getCommand())) {
            sendChattyDays(messageDTO.getChatId(), messageDTO.getMessageId());
        }
        if (messageDTO.getUserText().startsWith(BotCommands.SEND_TO_CHAT.getCommand())) {
            String userText = messageDTO.getUserText();
            String[] split = userText.replace(BotCommands.SEND_TO_CHAT.getCommand(), "").split(";");
            sendMessage(Long.valueOf(split[1].trim()), split[2].trim());
        }
    }
}
