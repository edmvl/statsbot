package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
public class HelpService implements CommandExecutable {
    private final MessageExecutor messageExecutor;

    public HelpService(MessageExecutor messageExecutor) {
        this.messageExecutor = messageExecutor;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().equals(BotCommands.START_COMMAND.getCommand())) {
            final String eof = "\n";
            StringBuilder message = new StringBuilder();
            if (messageDTO.getChatId() < 0) {
                messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
                message.append("Привет, вот что я пока умею:").append(eof);
                message.append(BotCommands.REMINDER.getCommand()).append(" : ").append(BotCommands.REMINDER.getExplainer()).append(eof);
                message.append(BotCommands.REMOVE_REMINDER.getCommand()).append(" : ").append(BotCommands.REMOVE_REMINDER.getExplainer()).append(eof);
                message.append(BotCommands.QUIZ.getCommand()).append(" : ").append(BotCommands.QUIZ.getExplainer()).append(eof);
                message.append(BotCommands.STATUS.getCommand()).append(" : ").append(BotCommands.STATUS.getExplainer()).append(eof);
            } else {
                message.append("Для начала нажмите кнопку Меню").append(eof);
            }
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(messageDTO.getChatId());
            sendMessage.setText(message.toString());
            messageExecutor.sendMessage(sendMessage);
        }
    }
}
