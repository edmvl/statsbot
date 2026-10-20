package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.WordsFilter;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.WordsFilterRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class WordsFilterService implements CommandExecutable, NeedProcessMessage {

    /**
     * Telegram-бот, от имени которого приходят сообщения анонимных
     * администраторов ({@code GroupAnonymousBot}). Реальный id автора
     * в таком сообщении не виден, поэтому считаем его админом.
     */
    private static final long GROUP_ANONYMOUS_BOT_ID = 1087968824L;

    private final MessageExecutor messageExecutor;

    private final WordsFilterRepo wordsFilterRepo;

    private final SettingsService settingsService;

    public WordsFilterService(MessageExecutor messageExecutor, WordsFilterRepo wordsFilterRepo, SettingsService settingsService) {
        this.messageExecutor = messageExecutor;
        this.wordsFilterRepo = wordsFilterRepo;
        this.settingsService = settingsService;
    }

    public void deleteByFilter(MessageDTO messageDTO, Update update) {
        if (messageDTO.isServiceMessage() || Objects.isNull(messageDTO.getUserText())) {
            return;
        }
        // Сообщения супер-админа и админов чата не трогаем — в том числе отправленные анонимно.
        if (isPrivileged(messageDTO, update)) {
            return;
        }
        List<String> words = Arrays.stream(messageDTO.getUserText().split(" ")).map(String::toLowerCase).toList();
        List<String> wordsToFilter = wordsFilterRepo.findAll().stream().map(WordsFilter::getWord).map(String::toLowerCase).toList();
        if (wordsToFilter.stream().anyMatch(s -> words.stream().anyMatch(s.toLowerCase()::equals))) {
            messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
        }
        if (settingsService.isEnabled(messageDTO.getChatId().toString(), ChatSettingConstants.TRIP_CHANNEL)) {
            Message message = update.getMessage();
            List<MessageEntity> entities = Objects.nonNull(message) ? message.getEntities() : null;
            if (Objects.nonNull(entities)) {
                long messagesWithUrl = entities.stream().filter(messageEntity -> Objects.nonNull(messageEntity.getUrl())).count();
                if (messagesWithUrl > 0) {
                    messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
                }
            }
        }
    }

    /**
     * Супер-админ бота, админ чата ({@code is_admin}) или анонимный администратор
     * (Telegram подменяет отправителя ботом {@code GroupAnonymousBot}).
     */
    private boolean isPrivileged(MessageDTO messageDTO, Update update) {
        Long userId = messageDTO.getUserId();
        if (Objects.nonNull(userId)) {
            if (AccessConstants.SUPER_ADMIN_ID.equals(userId) || GROUP_ANONYMOUS_BOT_ID == userId) {
                return true;
            }
            if (settingsService.isUserAdmin(messageDTO.getChatId(), userId)) {
                return true;
            }
        }
        Message message = update.getMessage();
        // сообщение от имени канала (в т.ч. анонимный админ со связанным каналом)
        return Objects.nonNull(message) && Objects.nonNull(message.getSenderChat());
    }

    public void addWord(String word, Long userId, Long chatId, Integer messageId) {
        messageExecutor.deleteMessage(chatId, messageId);
        if (settingsService.isUserAdmin(chatId, userId)) {
            WordsFilter wordsFilter = new WordsFilter();
            wordsFilter.setWord(word);
            wordsFilterRepo.save(wordsFilter);
        }
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.ADD_WORD.getCommand())) {
            String[] s = messageDTO.getUserText().split(" ");
            if (s.length >= 2) {
                addWord(s[1], messageDTO.getUserId(), messageDTO.getChatId(), messageDTO.getMessageId());
            }
        }
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        deleteByFilter(messageDTO, update);
    }
}
