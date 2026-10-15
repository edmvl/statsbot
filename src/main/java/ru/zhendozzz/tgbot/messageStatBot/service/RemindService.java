package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Remind;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.RemindRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RemindService implements CommandExecutable {
    private final MessageExecutor messageExecutor;
    private final RemindRepo remindRepo;
    private final StatsService statsService;
    private final SettingsService settingsService;

    public RemindService(MessageExecutor messageExecutor, RemindRepo remindRepo, StatsService statsService, SettingsService settingsService) {
        this.messageExecutor = messageExecutor;
        this.remindRepo = remindRepo;
        this.statsService = statsService;
        this.settingsService = settingsService;
    }

    public void addReminder(Long chatId, Long userId, Integer replyMessageId, String text) {
        Remind remind = new Remind();
        remind.setReplyMessageId(replyMessageId.toString());
        remind.setChatId(chatId.toString());
        remind.setUserId(userId.toString());
        remind.setText(text);
        remind.setActive(true);
        remindRepo.save(remind);
    }

    public List<Remind> removeReminder(Long chatId, Integer replyMessageId) {
        List<Remind> reminds = remindRepo.getRemindByChatIdAndReplyMessageId(String.valueOf(chatId), String.valueOf(replyMessageId));
        reminds.forEach(remind -> remind.setActive(false));
        remindRepo.saveAll(reminds);
        return reminds;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        String userText = messageDTO.getUserText();
        String reminderText = userText.replaceFirst(BotCommands.REMINDER.getCommand(), "");
        if (userText.startsWith(BotCommands.REMINDER.getCommand())) {
            addReminder(messageDTO.getChatId(), messageDTO.getUserId(), messageDTO.getReplyMessageId(), reminderText);
            messageExecutor.sendMessage(messageDTO.getChatId(), reminderText, messageDTO.getReplyMessageId());
        }
        if (userText.startsWith(BotCommands.REMOVE_REMINDER.getCommand())) {
            List<Remind> reminds = removeReminder(messageDTO.getChatId(), messageDTO.getReplyMessageId());
            if (reminds.isEmpty()) {
                messageExecutor.sendMessage(messageDTO.getChatId(), "Напоминание не найдено, необходимо отметить сообщение, которое упоминается в напоминании", messageDTO.getReplyMessageId());
            } else {
                messageExecutor.sendMessage(messageDTO.getChatId(), "Напоминание остановлено", messageDTO.getReplyMessageId());
            }
        }
    }

    public void playReplyReminder() {
        List<String> distinctChatId = statsService.findAllChats();
        distinctChatId.forEach(s -> {
            List<Remind> allByActiveIsTrueAndChatId = remindRepo.findAllByActiveIsTrueAndChatId(s);
            allByActiveIsTrueAndChatId.forEach(remind -> messageExecutor.sendMessage(remind.getChatId(), remind.getText(), Integer.valueOf(remind.getReplyMessageId())));
        });
    }

    public void playReminder() {
        List<String> enableReminderChats = settingsService.findEnableReminderChats();
        enableReminderChats.forEach(chatId -> {
            messageExecutor.sendMessage(chatId, getTimeToNextSeason());
        });
    }

    public static String getTimeToNextSeason() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        LocalDate springStart = LocalDate.of(year, Month.MARCH, 1);
        LocalDate summerStart = LocalDate.of(year, Month.JUNE, 1);
        LocalDate autumnStart = LocalDate.of(year, Month.SEPTEMBER, 1);
        LocalDate winterStart = LocalDate.of(year, Month.DECEMBER, 1);
        LocalDate nextSeasonStart = null;
        String seasonName = "";
        if (today.isBefore(springStart)) {
            nextSeasonStart = springStart;
            seasonName = "весны";
        } else if (today.isBefore(summerStart)) {
            nextSeasonStart = summerStart;
            seasonName = "лета";
        } else if (today.isBefore(autumnStart)) {
            nextSeasonStart = autumnStart;
            seasonName = "осени";
        } else if (today.isBefore(winterStart)) {
            nextSeasonStart = winterStart;
            seasonName = "зимы";
        } else {
            nextSeasonStart = LocalDate.of(year + 1, Month.MARCH, 1);
            seasonName = "весны";
        }
        long daysUntil = ChronoUnit.DAYS.between(today, nextSeasonStart);
        return "До " + seasonName + " осталось дней: " + daysUntil;
    }
}
