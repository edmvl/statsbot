package ru.zhendozzz.tgbot.messageStatBot.app;

import ru.zhendozzz.tgbot.messageStatBot.processor.Processor;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Objects;

@Component
public class MyTelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    private Processor processor;

    private SettingsService settingsService;

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        clearUnavailableMark(update);
        processor.process(update);
    }

    /**
     * Бот получил сообщение из чата — значит чат существует (бота вернули или разблокировали),
     * снимаем отметку «недоступен», иначе рассылки остались бы выключенными навсегда.
     */
    private void clearUnavailableMark(Update update) {
        Long chatId = chatIdOf(update);
        if (Objects.nonNull(chatId) && Objects.nonNull(settingsService)) {
            settingsService.markAvailable(String.valueOf(chatId));
        }
    }

    private static Long chatIdOf(Update update) {
        if (Objects.isNull(update)) {
            return null;
        }
        if (Objects.nonNull(update.getMessage())) {
            return update.getMessage().getChatId();
        }
        if (Objects.nonNull(update.getEditedMessage())) {
            return update.getEditedMessage().getChatId();
        }
        if (Objects.nonNull(update.getChannelPost())) {
            return update.getChannelPost().getChatId();
        }
        if (Objects.nonNull(update.getEditedChannelPost())) {
            return update.getEditedChannelPost().getChatId();
        }
        if (Objects.nonNull(update.getCallbackQuery()) && Objects.nonNull(update.getCallbackQuery().getMessage())) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    @Autowired
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    @Autowired
    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

}
