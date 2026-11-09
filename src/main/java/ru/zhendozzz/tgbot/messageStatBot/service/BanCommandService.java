package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.util.Objects;

/**
 * Команда {@code .бан} — ответом на сообщение пользователя.
 * Создаёт обращение, сразу банит нарушителя и закрывает обращение.
 * Доступна только администраторам канала поездок.
 */
@Slf4j
@Service
public class BanCommandService implements CommandExecutable {

    private final FeedbackService feedbackService;
    private final SettingsService settingsService;
    private final MessageExecutor messageExecutor;

    public BanCommandService(FeedbackService feedbackService,
                             SettingsService settingsService,
                             MessageExecutor messageExecutor) {
        this.feedbackService = feedbackService;
        this.settingsService = settingsService;
        this.messageExecutor = messageExecutor;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (!messageDTO.getUserText().startsWith(BotCommands.BAN.getCommand())) {
            return;
        }
        Long chatId = messageDTO.getChatId();
        Long adminId = messageDTO.getUserId();
        if (chatId == null || adminId == null || !isTripAdmin(chatId, adminId)) {
            return;
        }

        Message command = update.getMessage();
        Message reply = command == null ? null : command.getReplyToMessage();
        User target = reply == null ? null : reply.getFrom();
        if (reply == null || target == null || target.getId() == null) {
            messageExecutor.sendMessage(chatId,
                    "Ответьте на сообщение пользователя, которого нужно заблокировать.",
                    messageDTO.getMessageId());
            messageExecutor.deleteMessage(chatId, messageDTO.getMessageId());
            return;
        }
        if (Objects.equals(target.getId(), adminId)
                || AccessConstants.SUPER_ADMIN_ID.equals(target.getId())
                || Boolean.TRUE.equals(target.getIsBot())) {
            messageExecutor.sendMessage(chatId, "Этого пользователя нельзя заблокировать.",
                    messageDTO.getMessageId());
            messageExecutor.deleteMessage(chatId, messageDTO.getMessageId());
            return;
        }

        String messageText = Objects.toString(reply.getText(), reply.getCaption());
        try {
            feedbackService.banByReply(String.valueOf(adminId), String.valueOf(target.getId()), messageText);
        } catch (Exception e) {
            log.error("Не удалось выполнить .бан для {} в чате {}: {}",
                    target.getId(), chatId, e.getMessage());
            messageExecutor.sendMessage(chatId, "Не удалось заблокировать пользователя.",
                    messageDTO.getMessageId());
        } finally {
            messageExecutor.deleteMessage(chatId, messageDTO.getMessageId());
        }
    }

    private boolean isTripAdmin(Long chatId, Long userId) {
        String tripChatId = settingsService.findTripChannelId();
        if (tripChatId == null || !tripChatId.equals(String.valueOf(chatId))) {
            return false;
        }
        if (AccessConstants.SUPER_ADMIN_ID.equals(userId)) {
            return true;
        }
        return settingsService.isUserAdmin(chatId, userId);
    }
}
