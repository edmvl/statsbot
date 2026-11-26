package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.WordsFilter;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.WordsFilterRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WordsFilterService implements CommandExecutable, NeedProcessMessage {

    /**
     * Telegram-бот, от имени которого приходят сообщения анонимных
     * администраторов ({@code GroupAnonymousBot}). Реальный id автора
     * в таком сообщении не виден, поэтому считаем его админом.
     */
    private static final long GROUP_ANONYMOUS_BOT_ID = 1087968824L;

    /**
     * Список стоп-слов кэшируется: {@code findAll()} на каждое сообщение — лишний запрос
     * на самом горячем пути и постоянный мусор в куче. Инвалидация — при добавлении слова.
     */
    private static final long WORDS_CACHE_TTL_MS = 60_000;

    /**
     * Упоминание в тексте: Telegram-боты обязаны иметь username, оканчивающийся на «bot»,
     * поэтому по одному виду {@code @username} можно отличить бота от человека.
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@[A-Za-z0-9_]+");

    private static final String MENTION_ENTITY = "mention";
    private static final String TEXT_MENTION_ENTITY = "text_mention";

    private volatile List<String> cachedWords = List.of();
    private volatile long cachedWordsAt = 0;

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final MessageExecutor messageExecutor;

    private final WordsFilterRepo wordsFilterRepo;

    private final SettingsService settingsService;

    private final LogService logService;

    public WordsFilterService(MessageExecutor messageExecutor, WordsFilterRepo wordsFilterRepo,
                              SettingsService settingsService, LogService logService) {
        this.messageExecutor = messageExecutor;
        this.wordsFilterRepo = wordsFilterRepo;
        this.settingsService = settingsService;
        this.logService = logService;
    }

    public void deleteByFilter(MessageDTO messageDTO, Update update) {
        if (messageDTO.isServiceMessage()) {
            return;
        }
        Message message = resolveMessage(update);
        // Сообщения супер-админа и админов чата не трогаем — в том числе отправленные анонимно.
        if (isPrivileged(messageDTO, message)) {
            return;
        }

        boolean deleted = false;
        if (Objects.nonNull(messageDTO.getUserText())) {
            List<String> words = Arrays.stream(messageDTO.getUserText().split(" "))
                    .map(word -> word.toLowerCase(Locale.ROOT))
                    .toList();
            List<String> wordsToFilter = wordsToFilter();
            if (wordsToFilter.stream().anyMatch(words::contains)) {
                deleteAndMark(messageDTO);
                deleted = true;
            }
        }
        if (Objects.nonNull(message)
                && settingsService.isEnabled(messageDTO.getChatId().toString(), ChatSettingConstants.TRIP_CHANNEL)
                && hasLink(message)) {
            if (!deleted) {
                deleteAndMark(messageDTO);
                deleted = true;
            }
        }
        // Чистка от ботов: чужое упоминание, url-кнопка или инлайн-сообщение,
        // отправленное пользователем через стороннего бота (via_bot).
        if (Objects.nonNull(message)
                && settingsService.isEnabled(messageDTO.getChatId().toString(), ChatSettingConstants.ENABLE_BOT_MENTION_FILTER)
                && (mentionsForeignBot(message) || hasUrlButton(message) || hasForeignViaBot(message))) {
            if (!deleted) {
                deleteAndMark(messageDTO);
            }
            muteAuthor(messageDTO, message);
        }
    }

    private void deleteAndMark(MessageDTO messageDTO) {
        messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
        logService.markDeleted(messageDTO.getChatId(), messageDTO.getMessageId());
    }

    private Message resolveMessage(Update update) {
        return Objects.nonNull(update.getMessage()) ? update.getMessage() : update.getEditedMessage();
    }

    /** Ссылка в тексте/подписи: {@code url} или скрытая {@code text_link} — у обеих есть url. */
    private boolean hasLink(Message message) {
        return hasUrlEntity(message.getEntities()) || hasUrlEntity(message.getCaptionEntities());
    }

    private boolean hasUrlEntity(List<MessageEntity> entities) {
        return Objects.nonNull(entities)
                && entities.stream().anyMatch(entity -> Objects.nonNull(entity) && Objects.nonNull(entity.getUrl()));
    }

    /**
     * Упоминание любого бота, кроме нашего: {@code text_mention} с флагом is_bot,
     * {@code mention} вида {@code @...bot}, а также запасной поиск по тексту —
     * некоторые боты присылают текст без entities.
     */
    private boolean mentionsForeignBot(Message message) {
        return hasForeignBotMention(message.getText(), message.getEntities())
                || hasForeignBotMention(message.getCaption(), message.getCaptionEntities());
    }

    /** Инлайн-режим чужого бота: автор — пользователь, но сообщение отправлено через бота. */
    private boolean hasForeignViaBot(Message message) {
        User viaBot = message.getViaBot();
        return Objects.nonNull(viaBot) && !isOwnBotUsername(viaBot.getUserName());
    }

    private boolean hasForeignBotMention(String text, List<MessageEntity> entities) {
        if (Objects.isNull(text)) {
            return false;
        }
        if (Objects.nonNull(entities)) {
            for (MessageEntity entity : entities) {
                if (Objects.isNull(entity) || Objects.isNull(entity.getType())) {
                    continue;
                }
                if (TEXT_MENTION_ENTITY.equals(entity.getType())) {
                    User user = entity.getUser();
                    if (Objects.nonNull(user) && Boolean.TRUE.equals(user.getIsBot())
                            && !isOwnBotUsername(user.getUserName())) {
                        return true;
                    }
                } else if (MENTION_ENTITY.equals(entity.getType())
                        && isForeignBotUsername(mentionText(text, entity))) {
                    return true;
                }
            }
        }
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find()) {
            if (isForeignBotUsername(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private String mentionText(String text, MessageEntity entity) {
        int start = Math.max(0, entity.getOffset());
        int end = Math.min(text.length(), start + entity.getLength());
        return start < end ? text.substring(start, end) : "";
    }

    /** Telegram требует, чтобы username бота оканчивался на «bot» (регистр не важен). */
    private boolean isForeignBotUsername(String username) {
        if (Objects.isNull(username)) {
            return false;
        }
        String name = username.trim();
        while (name.startsWith("@")) {
            name = name.substring(1);
        }
        return name.toLowerCase(Locale.ROOT).endsWith("bot") && !isOwnBotUsername(name);
    }

    private boolean isOwnBotUsername(String username) {
        if (Objects.isNull(username) || Objects.isNull(botUsername)) {
            return false;
        }
        return botUsername.replace("@", "").equalsIgnoreCase(username.replace("@", "").trim());
    }

    /** Ссылка, спрятанная под inline-кнопку: в тексте её нет, поэтому entities не помогают. */
    private boolean hasUrlButton(Message message) {
        InlineKeyboardMarkup markup = message.getReplyMarkup();
        if (Objects.isNull(markup) || Objects.isNull(markup.getKeyboard())) {
            return false;
        }
        for (List<InlineKeyboardButton> row : markup.getKeyboard()) {
            if (Objects.isNull(row)) {
                continue;
            }
            for (InlineKeyboardButton button : row) {
                if (Objects.nonNull(button) && Objects.nonNull(button.getUrl()) && !button.getUrl().isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ботов не ограничиваем: Telegram не даёт управлять правами ботов, да и автор — человек. */
    private void muteAuthor(MessageDTO messageDTO, Message message) {
        User from = message.getFrom();
        if (Objects.isNull(from) || Boolean.TRUE.equals(from.getIsBot()) || Objects.isNull(messageDTO.getUserId())) {
            return;
        }
        messageExecutor.restrictChatMember(messageDTO.getChatId(), messageDTO.getUserId());
    }

    /**
     * Супер-админ бота, админ чата ({@code is_admin}) или анонимный администратор
     * (Telegram подменяет отправителя ботом {@code GroupAnonymousBot}).
     */
    private boolean isPrivileged(MessageDTO messageDTO, Message message) {
        Long userId = messageDTO.getUserId();
        if (Objects.nonNull(userId)) {
            if (AccessConstants.SUPER_ADMIN_ID.equals(userId) || GROUP_ANONYMOUS_BOT_ID == userId) {
                return true;
            }
            if (settingsService.isUserAdmin(messageDTO.getChatId(), userId)) {
                return true;
            }
        }
        // сообщение от имени канала (в т.ч. анонимный админ со связанным каналом)
        return Objects.nonNull(message) && Objects.nonNull(message.getSenderChat());
    }

    public void addWord(String word, Long userId, Long chatId, Integer messageId) {
        messageExecutor.deleteMessage(chatId, messageId);
        if (settingsService.isUserAdmin(chatId, userId)) {
            WordsFilter wordsFilter = new WordsFilter();
            wordsFilter.setWord(word);
            wordsFilterRepo.save(wordsFilter);
            cachedWordsAt = 0;
        }
    }

    private List<String> wordsToFilter() {
        long now = System.currentTimeMillis();
        if (cachedWordsAt != 0 && now - cachedWordsAt < WORDS_CACHE_TTL_MS) {
            return cachedWords;
        }
        List<String> loaded = wordsFilterRepo.findAll().stream()
                .map(WordsFilter::getWord)
                .filter(Objects::nonNull)
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        cachedWords = loaded;
        cachedWordsAt = now;
        return loaded;
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
