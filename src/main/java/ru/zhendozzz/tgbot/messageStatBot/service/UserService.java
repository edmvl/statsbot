package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.UserAvatar;
import ru.zhendozzz.tgbot.messageStatBot.entity.TgUser;
import ru.zhendozzz.tgbot.messageStatBot.entity.UserNameHistory;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserNameHistoryRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ведёт таблицу {@code users}: обновляет ФИО/ник (с историей изменений) и аватар
 * при получении сообщения от пользователя.
 */
@Service
public class UserService implements NeedProcessMessage {

    /** Как часто (не чаще) проверять смену аватара по сообщениям пользователя. */
    private static final long AVATAR_REFRESH_INTERVAL_MS = Duration.ofMinutes(30).toMillis();

    private final UserRepo userRepo;
    private final UserNameHistoryRepo userNameHistoryRepo;
    private final MessageExecutor messageExecutor;
    private final Map<Long, Long> avatarChecks = new ConcurrentHashMap<>();

    public UserService(UserRepo userRepo, UserNameHistoryRepo userNameHistoryRepo, MessageExecutor messageExecutor) {
        this.userRepo = userRepo;
        this.userNameHistoryRepo = userNameHistoryRepo;
        this.messageExecutor = messageExecutor;
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        Long userIdLong = messageDTO.getUserId();
        if (Objects.isNull(userIdLong)) {
            return;
        }
        String userId = String.valueOf(userIdLong);

        TgUser user = userRepo.findById(userId).orElse(null);
        boolean isNew = Objects.isNull(user);
        if (isNew) {
            user = new TgUser();
            user.setId(userId);
        }

        String firstName = messageDTO.getUserFirstName();
        String lastName = messageDTO.getUserLastName();
        String userName = messageDTO.getUserName();

        // Сравниваем по «отображаемому имени», а не по сырым колонкам: Telegram иногда
        // присылает имя то целиком в first_name, то раздельно first_name+last_name —
        // иначе в истории появляются «качающиеся» дубли.
        String newDisplayName = displayName(firstName, lastName);
        String currentDisplayName = displayName(user.getFirstName(), user.getLastName());
        boolean nameChanged = isNew
                || !newDisplayName.equals(currentDisplayName)
                || !normalize(userName).equals(normalize(user.getUserName()));

        user.setFirstName(normalizeOrNull(firstName));
        user.setLastName(normalizeOrNull(lastName));
        user.setUserName(normalizeOrNull(userName));
        user.setUpdatedAt(LocalDateTime.now());

        // Аватар проверяем по сообщениям, но не чаще заданного интервала; скачиваем только при смене.
        if (isNew || shouldCheckAvatar(userIdLong)) {
            UserAvatar avatar = messageExecutor.getUserAvatar(userIdLong);
            if (Objects.nonNull(avatar) && !Objects.equals(user.getAvatarFileId(), avatar.fileUniqueId())) {
                String avatarPath = messageExecutor.downloadUserAvatar(userIdLong, avatar.fileId());
                if (Objects.nonNull(avatarPath)) {
                    user.setAvatarFileId(avatar.fileUniqueId());
                    user.setAvatarPath(avatarPath);
                }
            }
        }

        userRepo.save(user);

        if (nameChanged) {
            UserNameHistory history = new UserNameHistory();
            history.setUserId(userId);
            history.setFirstName(normalizeOrNull(firstName));
            history.setLastName(normalizeOrNull(lastName));
            history.setUserName(normalizeOrNull(userName));
            history.setChangedAt(LocalDateTime.now());
            userNameHistoryRepo.save(history);
        }
    }

    /** «Имя Фамилия» из отдельных полей — для сравнения без учёта перекладки полей. */
    private String displayName(String firstName, String lastName) {
        return (normalize(firstName) + " " + normalize(lastName)).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOrNull(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean shouldCheckAvatar(Long userId) {
        long now = System.currentTimeMillis();
        Long last = avatarChecks.get(userId);
        if (Objects.isNull(last) || now - last >= AVATAR_REFRESH_INTERVAL_MS) {
            avatarChecks.put(userId, now);
            return true;
        }
        return false;
    }
}
