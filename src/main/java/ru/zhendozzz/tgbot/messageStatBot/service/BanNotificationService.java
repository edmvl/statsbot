package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.BannedUser;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.BannedUserRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Если пишет заблокированный пользователь — сообщаем ему об этом и даём ссылку
 * на жалобу, по которой его заблокировали. Не чаще одного раза в час на пользователя,
 * чтобы не спамить.
 */
@Slf4j
@Service
public class BanNotificationService implements NeedProcessMessage {

    private static final Duration THROTTLE = Duration.ofHours(1);

    private final BannedUserRepo bannedUserRepo;
    private final MessageExecutor messageExecutor;
    private final LogRepo logRepo;
    private final Map<Long, Instant> lastNotified = new ConcurrentHashMap<>();

    public BanNotificationService(BannedUserRepo bannedUserRepo, MessageExecutor messageExecutor, LogRepo logRepo) {
        this.bannedUserRepo = bannedUserRepo;
        this.messageExecutor = messageExecutor;
        this.logRepo = logRepo;
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        Long userId = messageDTO.getUserId();
        if (Objects.isNull(userId) || messageDTO.isServiceMessage() || messageDTO.isEdited()) {
            return;
        }
        BannedUser ban = bannedUserRepo.findFirstByUserIdOrderByBannedAtDesc(String.valueOf(userId))
                .orElse(null);
        if (Objects.isNull(ban)) {
            return;
        }

        Instant now = Instant.now();
        Instant last = lastNotified.get(userId);
        if (Objects.nonNull(last) && last.plus(THROTTLE).isAfter(now)) {
            return;
        }
        lastNotified.put(userId, now);

        StringBuilder text = new StringBuilder("🚫 Вы заблокированы");
        String chatName = Objects.isNull(ban.getChatId()) ? null : logRepo.findChatNameByChatId(ban.getChatId());
        if (Objects.nonNull(chatName) && !chatName.isEmpty()) {
            text.append(" в чате «").append(chatName).append("»");
        }
        text.append('.');
        if (Objects.nonNull(ban.getFeedbackId())) {
            text.append("\nПричина — жалоба: ").append(FeedbackService.FEEDBACK_URL).append(ban.getFeedbackId());
        }
        messageExecutor.sendMessage(String.valueOf(userId), text.toString());
    }
}
