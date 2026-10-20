package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.InterestedViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.SubscriptionGroupDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Interested;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.InterestedRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InterestedService implements CommandExecutable, NeedProcessMessage {

    private final MessageExecutor messageExecutor;
    private final InterestedRepo interestedRepo;

    public InterestedService(MessageExecutor messageExecutor, InterestedRepo interestedRepo) {
        this.messageExecutor = messageExecutor;
        this.interestedRepo = interestedRepo;
    }


    public void sendInterested(MessageDTO messageDTO) {
        List<String> userIdsForSend = interestedRepo.getInterestedByInterestIdAndChatId(
                String.valueOf(messageDTO.getUserId()),
                String.valueOf(messageDTO.getChatId())
        ).stream().map(Interested::getUserId).toList();
        if (userIdsForSend.isEmpty()) {
            return;
        }
        String message = String.format("%s %s %s %s:",
                Objects.toString(messageDTO.getUserFirstName(), ""),
                Objects.toString(messageDTO.getUserLastName(), ""),
                Objects.toString(messageDTO.getUserName(), ""),
                Objects.toString(messageDTO.getUserText(), "")
        );
        userIdsForSend.forEach(userIdForSend -> messageExecutor.sendMessage(userIdForSend, message));
    }

    /**
     * Подписывает пользователя {@code userId} на сообщения автора {@code interestId} в чате {@code chatId}.
     */
    public void subscribe(String userId, String chatId, String interestId) {
        if (isBlank(userId) || isBlank(chatId) || isBlank(interestId)) {
            return;
        }
        if (!interestedRepo.findByUserIdAndChatIdAndInterestId(userId, chatId, interestId).isEmpty()) {
            return;
        }
        Interested interested = new Interested();
        interested.setUserId(userId);
        interested.setChatId(chatId);
        interested.setInterestId(interestId);
        interestedRepo.save(interested);
    }

    /**
     * Отписывает пользователя {@code userId} от сообщений автора {@code interestId} в чате {@code chatId}.
     * Если чат не задан — отписывает во всех чатах.
     */
    public void unsubscribe(String userId, String chatId, String interestId) {
        if (isBlank(userId) || isBlank(interestId)) {
            return;
        }
        if (isBlank(chatId)) {
            interestedRepo.deleteByUserIdAndInterestId(userId.trim(), interestId.trim());
            return;
        }
        interestedRepo.deleteByUserIdAndChatIdAndInterestId(userId.trim(), chatId.trim(), interestId.trim());
    }

    /**
     * Список id авторов, на которых подписан пользователь в конкретном чате.
     */
    public List<String> findSubscribedInterestIds(String userId, String chatId) {
        if (isBlank(userId) || isBlank(chatId)) {
            return List.of();
        }
        return interestedRepo.findByUserIdAndChatId(userId, chatId).stream()
                .map(Interested::getInterestId)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Все подписки с данными пользователей (для админской страницы).
     */
    public List<InterestedViewDto> findAllWithUsers() {
        return interestedRepo.findAllWithUsers();
    }

    /**
     * Все подписки, сгруппированные по подписавшемуся: данные подписчика
     * показываются один раз, а не для каждой подписки.
     */
    public List<SubscriptionGroupDto> findAllGroupedBySubscriber() {
        Map<String, List<InterestedViewDto>> bySubscriber = new LinkedHashMap<>();
        for (InterestedViewDto row : interestedRepo.findAllWithUsers()) {
            bySubscriber.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row);
        }
        List<SubscriptionGroupDto> groups = new ArrayList<>();
        bySubscriber.forEach((userId, interests) -> {
            InterestedViewDto first = interests.get(0);
            groups.add(new SubscriptionGroupDto(
                    userId,
                    first.getUserFirstName(),
                    first.getUserLastName(),
                    first.getUserName(),
                    first.getUserAvatarPath(),
                    interests));
        });
        return groups;
    }

    /** На кого подписан конкретный пользователь. */
    public List<InterestedViewDto> findBySubscriber(String userId) {
        if (isBlank(userId)) {
            return List.of();
        }
        return interestedRepo.findAllWithUsersByUserId(userId.trim());
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.INTERESTED.getCommand())) {
            String interestedId = messageDTO.getUserText().replaceFirst(BotCommands.INTERESTED.getCommand() + " ", "").trim();
            subscribe(String.valueOf(messageDTO.getUserId()), String.valueOf(messageDTO.getChatId()), interestedId);
        }
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        if (messageDTO.isServiceMessage()) {
            return;
        }
        sendInterested(messageDTO);
    }
}
