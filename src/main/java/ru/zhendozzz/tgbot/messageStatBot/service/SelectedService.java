package ru.zhendozzz.tgbot.messageStatBot.service;

import ru.zhendozzz.tgbot.messageStatBot.entity.Selected;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.SelectedRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
@Slf4j
public class SelectedService {

    /** Сколько последних дней считаем активностью: в розыгрыше участвуют только те, кто писал в чат за этот срок. */
    private static final int ACTIVE_DAYS = 30;

    /** Сколько раз пробуем отправить сообщение в Telegram (ответ {@code null} = отправка не удалась). */
    private static final int SEND_ATTEMPTS = 3;

    /** Базовая пауза перед повтором, растёт линейно: 500 мс, затем 1000 мс. */
    private static final long SEND_RETRY_DELAY_MS = 500L;

    /** Статусы Telegram, при которых человек действительно числится в чате. */
    private static final Set<String> CHAT_MEMBER_STATUSES =
            Set.of("creator", "administrator", "member", "restricted");

    /** Сколько кандидатов максимум проверяем через getChatMember, чтобы не упереться в лимиты Bot API. */
    private static final int MEMBERSHIP_CHECKS = 10;

    /** Генератор для перемешивания кандидатов; в тестах подменяется на seed-вариант. */
    Random random = new Random();

    private final SelectedRepo stinkyRepo;
    private final LogRepo logRepo;
    private final StatsService statsService;
    private final SettingsService settingsService;
    private final MessageExecutor messageExecutor;

    public SelectedService(SelectedRepo stinkyRepo, LogRepo logRepo, StatsService statsService, SettingsService settingsService, MessageExecutor messageExecutor) {
        this.stinkyRepo = stinkyRepo;
        this.logRepo = logRepo;
        this.statsService = statsService;
        this.settingsService = settingsService;
        this.messageExecutor = messageExecutor;
    }

    public Selected findByMessage(String chatId, LocalDate date) {
        return stinkyRepo.findByChatIdAndDate(chatId, date);
    }

    public void save(String chatId, String userId, LocalDate date) {
        Selected selected = new Selected();
        selected.setChatId(chatId);
        selected.setUserId(userId);
        selected.setDate(date);
        stinkyRepo.save(selected);
    }

    public String getStinky(String chatId) {
        List<String> candidates = logRepo.findDistinctUserIdByChatIdSince(chatId, LocalDateTime.now().minusDays(ACTIVE_DAYS));
        if (candidates.isEmpty()) {
            // В затихшем чате активных нет — разыгрываем среди всех участников, как раньше.
            candidates = logRepo.findDistinctUserIdByChatId(chatId);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("В чате " + chatId + " нет участников для розыгрыша");
        }
        //Тянуть статусы всех участников — это сотни запросов в Bot API, поэтому
        //перемешиваем и проверяем не больше MEMBERSHIP_CHECKS кандидатов.
        List<String> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        int checks = Math.min(MEMBERSHIP_CHECKS, shuffled.size());
        for (int i = 0; i < checks; i++) {
            if (isChatMember(chatId, shuffled.get(i))) {
                return shuffled.get(i);
            }
            log.info("{} из чата {} уже не участник, берём следующего", shuffled.get(i), chatId);
        }
        return null;
    }

    /**
     * Проверяет, что человек всё ещё в чате. Если Telegram не ответил
     * (сеть, флуд-лимит), не выкидываем участника из розыгрыша.
     */
    private boolean isChatMember(String chatId, String userId) {
        String status = messageExecutor.getUserStatusInChat(chatId, userId);
        if (Objects.isNull(status)) {
            log.warn("Не удалось узнать статус {} в чате {}, считаем участником", userId, chatId);
            return true;
        }
        return CHAT_MEMBER_STATUSES.contains(status);
    }

    public void sendSelected(Long chatId, Integer messageId) {
        sendSelected(chatId);
        messageExecutor.deleteMessage(chatId, messageId);
    }

    public void sendSelectedAllChat() {
        List<String> chatIds = statsService.findAllChats();
        for (String chatId : chatIds) {
            if (settingsService.isDisabled(chatId, ChatSettingConstants.ENABLE_SELECTED)) {
                continue;
            }
            try {
                sendSelected(Long.valueOf(chatId));
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    public void sendSelected(Long chatId) {
        String chatIdString = chatId.toString();
        Selected existedSelected = findByMessage(chatIdString, LocalDate.now());
        boolean redrawn = false;

        String stinkyUserId = Objects.isNull(existedSelected) ? null : existedSelected.getUserId();
        if (Objects.nonNull(stinkyUserId) && !isChatMember(chatIdString, stinkyUserId)) {
            log.warn("{} больше не в чате {}, переразыгрываем", stinkyUserId, chatIdString);
            stinkyUserId = null;
        }
        if (Objects.isNull(stinkyUserId)) {
            stinkyUserId = getStinky(chatIdString);
            if (Objects.isNull(stinkyUserId)) {
                log.error("В чате {} не нашлось участника, розыгрыш пропущен", chatIdString);
                return;
            }
            redrawn = true;
        }

        SendMessage sendMessage = buildSelectedMessage(chatId, stinkyUserId, redrawn);
        if (!sendWithRetry(sendMessage)) {
            // Не отправили — не фиксируем выбор: вечером разыграем заново.
            log.error("Сообщение о проставляющемся в чат {} не доставлено, розыгрыш не сохранён", chatId);
            return;
        }
        if (redrawn) {
            persistSelection(existedSelected, chatIdString, stinkyUserId);
        }
    }

    private void persistSelection(Selected existedSelected, String chatId, String userId) {
        if (Objects.isNull(existedSelected)) {
            save(chatId, userId, LocalDate.now());
            return;
        }
        existedSelected.setUserId(userId);
        stinkyRepo.save(existedSelected);
    }

    private SendMessage buildSelectedMessage(Long chatId, String stinkyUserId, boolean searching) {
        String text = searching
                ? "Поиск спонсора нашей пьянки... Сегодня нам проставляется:\n"
                : "Сегодня нам проставляется:\n";
        String userNames = String.join(",", logRepo.findUserNameByUserId(stinkyUserId));
        User user = new User(Long.valueOf(stinkyUserId), userNames, false);
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setUser(user);
        messageEntity.setOffset(text.length());
        String userIdentityText = user.getFirstName() + " " + (Objects.nonNull(user.getLastName()) ? user.getLastName() : "") + "\n";
        text += userIdentityText;
        messageEntity.setLength(userIdentityText.length());
        messageEntity.setType("text_mention");
        SendMessage sendMessage = new SendMessage();
        sendMessage.setEntities(List.of(messageEntity));
        sendMessage.setText(text);
        sendMessage.setChatId(chatId);
        return sendMessage;
    }

    /** Отправляет сообщение с повторами: {@code MessageExecutor} возвращает {@code null}, если Telegram отклонил запрос. */
    private boolean sendWithRetry(SendMessage sendMessage) {
        for (int attempt = 1; attempt <= SEND_ATTEMPTS; attempt++) {
            try {
                if (Objects.nonNull(messageExecutor.sendMessage(sendMessage))) {
                    return true;
                }
                log.warn("Отправка в чат {} не удалась (попытка {}/{})", sendMessage.getChatId(), attempt, SEND_ATTEMPTS);
            } catch (RuntimeException e) {
                log.warn("Отправка в чат {} упала: {} (попытка {}/{})",
                        sendMessage.getChatId(), e.getMessage(), attempt, SEND_ATTEMPTS);
            }
            if (attempt < SEND_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(SEND_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
