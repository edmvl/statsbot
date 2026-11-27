package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Фильтр рекламы каналов: удаляет сообщения, где упоминается публичный канал
 * ({@code @channel}) и текст выглядит как реклама.
 *
 * <p>Задуман для чата попутчиков, где упоминания редки: на каждое сообщение с рекламными
 * словами сервис резолвит ник через {@code getChat}. Резолв кэшируется (1 час на канал,
 * 10 минут на отказ), поэтому запросов к Telegram единицы в сутки — лимиты Bot API
 * (порядка 30 запросов в секунду) далеко.</p>
 */
@Service
@Slf4j
public class AdFilterService implements NeedProcessMessage {

    /** Сколько держать в кэше положительный и отрицательный ответ getChat. */
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;
    private static final long NEGATIVE_CACHE_TTL_MS = 10 * 60 * 1000L;

    private static final long GROUP_ANONYMOUS_BOT_ID = 1087968824L;

    /** Больше трёх ников в одном сообщении не проверяем — это уже не реклама, а простыня. */
    private static final int MAX_MENTIONS = 3;

    /** Ник в тексте: @username (минимум 4 символа, как требует Telegram). */
    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{4,32})");

    /** Ссылка на канал: t.me/username или https://t.me/username. */
    private static final Pattern TME_LINK = Pattern.compile("(?:https?://)?t\\.me/([A-Za-z0-9_]{4,32})");

    /** Сильные признаки рекламы: сам товар или действие. */
    private static final List<String> STRONG_MARKERS = List.of(
            "плагин", "мод ", "моды", "чит", "взлом", "промокод", "скачать", "обход",
            "подписк", "ayugram", "extera", "exteragram", "tgstat", "накрутк");

    /** Слабые: по одному не срабатываем, нужно два и больше. */
    private static final List<String> WEAK_MARKERS = List.of(
            "лучш", "канал", "бесплатн", "ссылк", "телеграм", "сервис", "бот ");

    private final MessageExecutor messageExecutor;
    private final SettingsService settingsService;
    private final LogService logService;

    private final Map<String, CachedChat> chatCache = new ConcurrentHashMap<>();

    @Value("${telegram.bot.username}")
    private String botUsername;

    public AdFilterService(MessageExecutor messageExecutor, SettingsService settingsService,
                           LogService logService) {
        this.messageExecutor = messageExecutor;
        this.settingsService = settingsService;
        this.logService = logService;
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        if (messageDTO.isServiceMessage()
                || !settingsService.isEnabled(messageDTO.getChatId(), ChatSettingConstants.ENABLE_AD_FILTER)) {
            return;
        }
        Message message = Objects.nonNull(update.getMessage()) ? update.getMessage() : update.getEditedMessage();
        if (Objects.isNull(message) || isPrivileged(messageDTO, message)) {
            return;
        }
        String text = textOf(message);
        if (Objects.isNull(text) || text.isBlank()) {
            return;
        }
        int strong = countMarkers(text, STRONG_MARKERS);
        int weak = countMarkers(text, WEAK_MARKERS);
        if (strong == 0 && weak < 2) {
            return;
        }
        for (String username : usernames(message, text)) {
            Chat chat = resolve(username);
            if (isPublicChannel(chat) && !isCurrentChat(chat, messageDTO)) {
                remove(messageDTO, message, username, text, strong, weak);
                return;
            }
        }
    }

    /** Публичный канал (не супергруппа и не человек) — только он считается рекламой. */
    private boolean isPublicChannel(Chat chat) {
        return Objects.nonNull(chat) && "channel".equalsIgnoreCase(chat.getType());
    }

    /** Упоминание самого чата (например, ссылка на этот же чат) — не реклама. */
    private boolean isCurrentChat(Chat chat, MessageDTO messageDTO) {
        return Objects.nonNull(chat.getId()) && chat.getId().equals(messageDTO.getChatId());
    }

    private void remove(MessageDTO messageDTO, Message message, String username, String text,
                        int strong, int weak) {
        log.info("ad-filter: чат {}, автор {} — реклама канала @{} (сильных {}, слабых {}): {}",
                messageDTO.getChatId(), messageDTO.getUserId(), username, strong, weak, shortText(text));
        messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
        logService.markDeleted(messageDTO.getChatId(), messageDTO.getMessageId());
        muteAuthor(messageDTO, message);
    }

    /**
     * Мьют автора на час — как в «Запрете ботов»: одного удаления мало,
     * спамер просто пришлёт следующее сообщение.
     */
    private void muteAuthor(MessageDTO messageDTO, Message message) {
        User from = message.getFrom();
        if (Objects.isNull(from) || Boolean.TRUE.equals(from.getIsBot()) || Objects.isNull(messageDTO.getUserId())) {
            return;
        }
        log.info("ad-filter: автор {} в чате {} ограничен на час",
                messageDTO.getUserId(), messageDTO.getChatId());
        messageExecutor.restrictChatMember(messageDTO.getChatId(), messageDTO.getUserId());
    }

    /** Резолв ника через getChat с кэшем: публичный канал, иначе {@code null}. */
    private Chat resolve(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedChat cached = chatCache.get(key);
        if (Objects.nonNull(cached) && cached.expiresAt > now) {
            return cached.chat;
        }
        Chat chat;
        try {
            chat = messageExecutor.getChat(username);
        } catch (Exception e) {
            log.debug("ad-filter: getChat {} не удался: {}", username, e.getMessage());
            chat = null;
        }
        long ttl = Objects.isNull(chat) ? NEGATIVE_CACHE_TTL_MS : CACHE_TTL_MS;
        chatCache.put(key, new CachedChat(chat, now + ttl));
        return chat;
    }

    private Set<String> usernames(Message message, String text) {
        Set<String> names = new LinkedHashSet<>();
        collectEntityMentions(message.getEntities(), text, names);
        collectEntityMentions(message.getCaptionEntities(), text, names);
        Matcher mention = MENTION.matcher(text);
        while (mention.find()) {
            names.add(mention.group(1));
        }
        Matcher link = TME_LINK.matcher(text);
        while (link.find()) {
            names.add(link.group(1));
        }
        if (Objects.nonNull(botUsername)) {
            names.remove(botUsername.replace("@", ""));
        }
        return names.stream().limit(MAX_MENTIONS).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void collectEntityMentions(List<MessageEntity> entities, String text, Set<String> names) {
        if (Objects.isNull(entities)) {
            return;
        }
        for (MessageEntity entity : entities) {
            if (Objects.isNull(entity) || Objects.isNull(entity.getType())) {
                continue;
            }
            if ("mention".equals(entity.getType())) {
                int start = Math.max(0, entity.getOffset());
                int end = Math.min(text.length(), start + entity.getLength());
                if (start < end) {
                    String token = text.substring(start, end).replace("@", "");
                    if (!token.isBlank()) {
                        names.add(token);
                    }
                }
            } else if ("text_mention".equals(entity.getType())) {
                User user = entity.getUser();
                if (Objects.nonNull(user) && Objects.nonNull(user.getUserName())) {
                    names.add(user.getUserName());
                }
            }
        }
    }

    private int countMarkers(String text, List<String> markers) {
        String lower = text.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String marker : markers) {
            if (lower.contains(marker)) {
                count++;
            }
        }
        return count;
    }

    private String textOf(Message message) {
        if (Objects.nonNull(message.getText())) {
            return message.getText();
        }
        return message.getCaption();
    }

    /** Супер-админ, админ чата и анонимный админ неприкосновенны. */
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
        return Objects.nonNull(message.getSenderChat());
    }

    private String shortText(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
    }

    /** Кэш резолва: {@code chat == null} — ник не резолвится (человек или закрытый канал). */
    private static final class CachedChat {
        private final Chat chat;
        private final long expiresAt;

        private CachedChat(Chat chat, long expiresAt) {
            this.chat = chat;
            this.expiresAt = expiresAt;
        }
    }
}
