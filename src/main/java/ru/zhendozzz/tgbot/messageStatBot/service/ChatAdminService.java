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
 * Права администратора в чате — без задержек.
 *
 * <p>Порядок проверки: супер-админ бота → флаг в {@code settings} (один запрос к БД,
 * без Telegram) → живой статус через {@code getChatMember}. Если живой статус
 * «creator»/«administrator», пользователь сразу же записывается в админы бота,
 * поэтому следующая команда обходится без запроса к Telegram.</p>
 *
 * <p>Отрицательный результат не кэшируется: назначили человека админом — со следующей
 * команды он уже админ. Кэшируются только админские статусы (на 10 минут), чтобы
 * не дёргать Telegram на каждый стикер.</p>
 */
@Service
@Slf4j
public class ChatAdminService {

    private static final Set<String> ADMIN_STATUSES = Set.of("creator", "administrator");
    private static final long STATUS_TTL_MS = 10L * 60 * 1000;
    /** Больше этого числа записей кэш статусов не растёт. */
    private static final int MAX_ENTRIES = 5_000;

    private final SettingsService settingsService;
    private final MessageExecutor messageExecutor;
    /** Ключ «chatId:userId» → админский статус и время получения. */
    private final Map<String, CachedStatus> adminStatuses = new ConcurrentHashMap<>();

    public ChatAdminService(SettingsService settingsService, MessageExecutor messageExecutor) {
        this.settingsService = settingsService;
        this.messageExecutor = messageExecutor;
    }

    /**
     * Админ ли пользователь в чате. Живой админ сразу становится админом бота.
     */
    public boolean isAdmin(Long chatId, Long userId) {
        if (Objects.isNull(chatId) || Objects.isNull(userId)) {
            return false;
        }
        if (AccessConstants.SUPER_ADMIN_ID.equals(userId)) {
            return true;
        }
        if (settingsService.isUserAdmin(chatId, userId)) {
            return true;
        }
        String status = chatStatus(chatId, userId);
        if (!ADMIN_STATUSES.contains(status)) {
            return false;
        }
        settingsService.setAdmin(String.valueOf(chatId), String.valueOf(userId), true);
        log.info("Живой админ {} добавлен в админы бота в чате {}", userId, chatId);
        return true;
    }

    /**
     * Статус участника в чате (creator / administrator / member / restricted / left / kicked).
     * Кэшируются только админские статусы.
     */
    public String chatStatus(Long chatId, Long userId) {
        if (Objects.isNull(chatId) || Objects.isNull(userId)) {
            return null;
        }
        String key = chatId + ":" + userId;
        long now = System.currentTimeMillis();
        CachedStatus cached = adminStatuses.get(key);
        if (Objects.nonNull(cached) && now - cached.receivedAt < STATUS_TTL_MS) {
            return cached.status;
        }
        String status = messageExecutor.getUserStatusInChat(chatId, userId);
        if (ADMIN_STATUSES.contains(status)) {
            if (adminStatuses.size() > MAX_ENTRIES) {
                adminStatuses.clear();
            }
            adminStatuses.put(key, new CachedStatus(status, now));
        } else {
            adminStatuses.remove(key);
        }
        return status;
    }

    private static class CachedStatus {

        private final String status;
        private final long receivedAt;

        private CachedStatus(String status, long receivedAt) {
            this.status = status;
            this.receivedAt = receivedAt;
        }
    }
}
