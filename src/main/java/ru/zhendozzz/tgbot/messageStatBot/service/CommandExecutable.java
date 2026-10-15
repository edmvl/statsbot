package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;

public interface CommandExecutable {
    void executeCommand(MessageDTO messageDTO, Update update);
}
