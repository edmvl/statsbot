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

        boolean nameChanged = isNew
                || !Objects.equals(user.getFirstName(), firstName)
                || !Objects.equals(user.getLastName(), lastName)
                || !Objects.equals(user.getUserName(), userName);

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUserName(userName);
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
            history.setFirstName(firstName);
            history.setLastName(lastName);
            history.setUserName(userName);
            history.setChangedAt(LocalDateTime.now());
            userNameHistoryRepo.save(history);
        }
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
