package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подтягивает живых администраторов чата в админы бота.
 *
 * <p>Права живого админа Telegram видны только через {@code getChatMember} — в самом
 * сообщении признака «я админ» нет. Чтобы не дёргать API на каждое сообщение,
 * проверяем только команды ({@code .стикер}, {@code .бан} и т.п.) и не чаще раза
 * в час на пару «чат + пользователь».</p>
 */
@Service
@Slf4j
public class AdminSyncService {

    private static final Set<String> ADMIN_STATUSES = Set.of("creator", "administrator");
    private static final long CHECK_INTERVAL_MS = 60L * 60 * 1000;
    /** Больше этого числа записей кэш проверок не растёт. */
    private static final int MAX_ENTRIES = 5_000;

    private final SettingsService settingsService;
    private final MessageExecutor messageExecutor;
    /** Ключ «chatId:userId» → время последней проверки статуса. */
    private final Map<String, Long> checkedAt = new ConcurrentHashMap<>();

    public AdminSyncService(SettingsService settingsService, MessageExecutor messageExecutor) {
        this.settingsService = settingsService;
        this.messageExecutor = messageExecutor;
    }

    /**
     * Если автор команды — админ или создатель чата в Telegram, делает его админом бота.
     */
    public void syncIfNeeded(Long chatId, Long userId) {
        if (Objects.isNull(chatId) || Objects.isNull(userId) || AccessConstants.SUPER_ADMIN_ID.equals(userId)) {
            return;
        }
        if (settingsService.isUserAdmin(chatId, userId)) {
            return;
        }
        String key = chatId + ":" + userId;
        long now = System.currentTimeMillis();
        Long lastCheck = checkedAt.get(key);
        if (Objects.nonNull(lastCheck) && now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        if (checkedAt.size() > MAX_ENTRIES) {
            checkedAt.clear();
        }
        checkedAt.put(key, now);

        String status = messageExecutor.getUserStatusInChat(chatId, userId);
        if (ADMIN_STATUSES.contains(status)) {
            settingsService.setAdmin(String.valueOf(chatId), String.valueOf(userId), true);
            log.info("Живой админ {} добавлен в админы бота в чате {}", userId, chatId);
        }
    }
}
