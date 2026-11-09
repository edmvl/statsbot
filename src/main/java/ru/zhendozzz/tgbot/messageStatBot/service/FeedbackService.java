package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import lombok.extern.slf4j.Slf4j;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMemberViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.BannedUser;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBackChat;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.BannedUserRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.FeedBackChatRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.FeedBackRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.KeyboardUtils;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class FeedbackService implements CommandExecutable {

    /** Ссылка на обращение: её получают админы и забаненные пользователи. */
    public static final String FEEDBACK_URL = "https://api.zhendozzz.ru/app/feedback-chat/";

    private final FeedBackRepo feedBackRepo;
    private final LogRepo logRepo;
    private final FeedBackChatRepo feedBackChatRepo;
    private final FileLoaderService fileLoaderService;
    private final MessageExecutor messageExecutor;
    private final BannedUserRepo bannedUserRepo;
    private final SettingsService settingsService;

    public FeedbackService(
            FeedBackRepo feedBackRepo,
            LogRepo logRepo,
            FeedBackChatRepo feedBackChatRepo,
            FileLoaderService fileLoaderService,
            MessageExecutor messageExecutor,
            BannedUserRepo bannedUserRepo,
            SettingsService settingsService
    ) {
        this.feedBackRepo = feedBackRepo;
        this.logRepo = logRepo;
        this.feedBackChatRepo = feedBackChatRepo;
        this.fileLoaderService = fileLoaderService;
        this.messageExecutor = messageExecutor;
        this.bannedUserRepo = bannedUserRepo;
        this.settingsService = settingsService;
    }

    public String processFeedback(String complaint, String userId, String targetUserId, List<MultipartFile> files) {
        FeedBack feedBack = new FeedBack();
        feedBack.setUserId(userId);
        String userName = userName(userId);
        feedBack.setUserName(userName);
        feedBack.setDescription(complaint);
        feedBack.setDateTime(LocalDateTime.now());

        String targetName = null;
        if (!isBlank(targetUserId)) {
            feedBack.setTargetUserId(targetUserId.trim());
            targetName = userName(targetUserId.trim());
            feedBack.setTargetUserName(targetName);
        }

        FeedBack savedFeedback = feedBackRepo.save(feedBack);
        if (files != null && !files.isEmpty()) {
            fileLoaderService.processFiles(files, String.valueOf(feedBack.getId()));
        }

        StringBuilder text = new StringBuilder();
        text.append(Objects.isNull(userName) || userName.isEmpty() ? "Пользователь" : userName)
                .append(" отправил обращение");
        if (targetName != null && !targetName.isEmpty()) {
            text.append(" на ").append(targetName);
        }
        text.append(":\n").append(complaint);

        List<String> tripGroupAdmins = feedBackRepo.getTripGroupAdmins();
        tripGroupAdmins.forEach(admin -> messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                "Посмотреть",
                text.toString(),
                FEEDBACK_URL + savedFeedback.getId(),
                admin)));
        return String.valueOf(savedFeedback.getId());
    }

    /**
     * Создаёт обращение по команде {@code .бан}: текст — «За сообщение в чате»,
     * текст исходного сообщения сохраняется скриншотом, затем сразу бан и закрытие.
     */
    public String banByReply(String adminId, String targetUserId, String messageText) {
        FeedBack feedback = new FeedBack();
        feedback.setUserId(adminId);
        feedback.setUserName(userName(adminId));
        feedback.setTargetUserId(targetUserId);
        feedback.setTargetUserName(userName(targetUserId));
        feedback.setDescription("За сообщение в чате");
        feedback.setDateTime(LocalDateTime.now());
        FeedBack saved = feedBackRepo.save(feedback);

        try {
            fileLoaderService.saveMessageScreenshot(saved.getId(), messageText);
        } catch (Exception e) {
            log.error("Не удалось сохранить скриншот сообщения для обращения {}: {}",
                    saved.getId(), e.getMessage());
        }
        banForFeedback(saved.getId(), adminId);
        return String.valueOf(saved.getId());
    }

    /** Обращение по id или {@code null}, если такого нет (без ленивого прокси, который падает в шаблоне). */
    public FeedBack findFeedbackOrNull(Long id) {
        return feedBackRepo.findById(id).orElse(null);
    }

    public List<FeedBackChat> findChatByFeedbackId(Long feedbackId) {
        return feedBackChatRepo.findByFeedbackId(feedbackId);
    }

    public void addChatByFeedbackId(Long feedbackId, String userId, String text, List<MultipartFile> files) {
        FeedBack feedback = feedBackRepo.findById(feedbackId).orElse(null);
        if (Objects.isNull(feedback)) {
            return;
        }
        FeedBackChat feedBackChat = new FeedBackChat();
        feedBackChat.setFeedbackId(feedbackId);
        feedBackChat.setDescription(text);
        feedBackChat.setUserId(userId);
        feedBackChat.setUserName(Objects.nonNull(userId) ? userName(userId) : "");
        feedBackChat.setDateTime(LocalDateTime.now());
        FeedBackChat saved = feedBackChatRepo.save(feedBackChat);

        if (Objects.nonNull(files) && files.stream().anyMatch(f -> Objects.nonNull(f) && !f.isEmpty())) {
            fileLoaderService.processFiles(files, commentFilesKey(saved.getId()));
        }

        boolean fromAuthor = Objects.nonNull(feedback.getUserId()) && feedback.getUserId().equals(userId);
        if (fromAuthor) {
            notifyAdmins(feedback, "Пользователь добавил комментарий к обращению");
        } else {
            messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                    "Посмотреть",
                    "Администратор ответил вам по заявке",
                    FEEDBACK_URL + feedbackId,
                    feedback.getUserId()));
        }
    }

    /** Вложения конкретного комментария (лежат в отдельном каталоге). */
    public List<String> findCommentFiles(Long commentId) {
        return fileLoaderService.getFiles(commentFilesKey(commentId));
    }

    public static String commentFilesKey(Long commentId) {
        return "fc-" + commentId;
    }

    /**
     * Указать (или изменить) пользователя, на которого обращение.
     *
     * <p>Менять может автор обращения или администратор, и только пока по обращению
     * никого не заблокировали — иначе список заблокированных разойдётся с обращением.</p>
     */
    public void updateTargetUser(Long feedbackId, String targetUserId, String actorId, boolean admin) {
        FeedBack feedback = feedBackRepo.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Обращение не найдено"));
        boolean owner = Objects.nonNull(actorId) && actorId.equals(feedback.getUserId());
        if (!admin && !owner) {
            throw new IllegalStateException("Менять пользователя может только автор обращения или администратор");
        }
        if (bannedUserRepo.existsByFeedbackId(feedbackId)) {
            throw new IllegalStateException("По обращению уже заблокирован пользователь");
        }

        String target = isBlank(targetUserId) ? null : targetUserId.trim();
        if (Objects.nonNull(target) && !target.matches("\\d{1,20}")) {
            throw new IllegalArgumentException("Некорректный пользователь");
        }
        feedback.setTargetUserId(target);
        feedback.setTargetUserName(isBlank(target) ? null : userName(target));
        feedBackRepo.save(feedback);

        // Автор пометил, на кого обращение — админам это важно знать, чтобы проверить и забанить.
        if (owner && !admin) {
            notifyAdmins(feedback, "Пользователь указал, на кого обращение");
        }
    }

    /** Закрыть обращение с причиной отказа — автору уходит сообщение. */
    public void closeFeedback(Long feedbackId, String reason, String adminId) {
        FeedBack feedback = feedBackRepo.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Обращение не найдено"));
        if (isBlank(reason)) {
            throw new IllegalArgumentException("Укажите причину отказа");
        }
        feedback.setFinished(true);
        feedback.setCloseReason(reason.trim());
        feedback.setClosedAt(LocalDateTime.now());
        feedBackRepo.save(feedback);

        messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                "Открыть",
                "Ваше обращение #" + feedbackId + " отклонено.\nПричина: " + reason.trim(),
                FEEDBACK_URL + feedbackId,
                feedback.getUserId()));
    }

    /** Переоткрыть обращение может её автор (например, если есть что добавить). */
    public void reopenFeedback(Long feedbackId, String userId) {
        FeedBack feedback = feedBackRepo.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Обращение не найдено"));
        if (!Objects.equals(feedback.getUserId(), userId)) {
            throw new IllegalStateException("Это не ваше обращение");
        }
        feedback.setFinished(false);
        feedback.setCloseReason(null);
        feedback.setClosedAt(null);
        feedBackRepo.save(feedback);
        notifyAdmins(feedback, "Пользователь переоткрыл обращение");
    }

    private void notifyAdmins(FeedBack feedback, String headline) {
        String label = userName(feedback.getUserId());
        String text = headline + " #" + feedback.getId()
                + (label.isEmpty() ? "" : " (" + label + ")");
        feedBackRepo.getTripGroupAdmins().forEach(admin -> messageExecutor.sendMessage(
                KeyboardUtils.getKeyboardMessage("Посмотреть", text, FEEDBACK_URL + feedback.getId(), admin)));
    }

    /** Пользователи канала поездок, на которых можно оставить обращение. */
    public List<ChatMemberViewDto> findComplainableUsers(String query) {
        String chatId = settingsService.findTripChannelId();
        if (isBlank(chatId)) {
            return List.of();
        }
        return logRepo.findChatMembers(chatId, isBlank(query) ? "" : query.trim(), 20);
    }

    /**
     * Блокирует пользователя по обращению: бан в канале поездок, запись в список
     * заблокированных и сообщение в канал со ссылкой на обращение.
     */
    public void banForFeedback(Long feedbackId, String adminId) {
        FeedBack feedback = feedBackRepo.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Обращение не найдено"));
        String targetUserId = feedback.getTargetUserId();
        if (isBlank(targetUserId)) {
            throw new IllegalStateException("В обращении не указан пользователь");
        }
        String chatId = settingsService.findTripChannelId();
        if (isBlank(chatId)) {
            throw new IllegalStateException("Не настроен канал поездок");
        }
        String label = userName(targetUserId);
        if (isBlank(label)) {
            label = targetUserId;
        }

        if (!bannedUserRepo.existsByUserIdAndChatId(targetUserId, chatId)) {
            messageExecutor.banUser(chatId, targetUserId);

            BannedUser ban = new BannedUser();
            ban.setUserId(targetUserId);
            ban.setChatId(chatId);
            ban.setUserName(label);
            ban.setFeedbackId(feedbackId);
            ban.setBannedBy(adminId);
            ban.setBannedAt(LocalDateTime.now());
            bannedUserRepo.save(ban);
        }

        feedback.setFinished(true);
        feedback.setCloseReason("Обращение рассмотрено: пользователь заблокирован.");
        feedback.setClosedAt(LocalDateTime.now());
        feedBackRepo.save(feedback);

        messageExecutor.sendMessage(chatId,
                "🚫 Пользователь " + label + " заблокирован по обращению.\n" + FEEDBACK_URL + feedbackId);
    }

    public List<BannedUser> findAllBans() {
        return bannedUserRepo.findAllByOrderByBannedAtDesc();
    }

    /**
     * Снимает бан: сначала в Telegram, затем удаляет запись из списка заблокированных.
     * Если Telegram не подтвердил операцию, запись остаётся — чтобы не потерять бан.
     */
    public void unban(Long banId) {
        BannedUser ban = bannedUserRepo.findById(banId)
                .orElseThrow(() -> new IllegalArgumentException("Запись о блокировке не найдена"));
        Long chatId = parseNumeric(ban.getChatId());
        Long userId = parseNumeric(ban.getUserId());
        if (Objects.isNull(chatId) || Objects.isNull(userId)) {
            throw new IllegalStateException("В записи о блокировке некорректный chat_id или user_id");
        }
        if (!messageExecutor.unbanUser(chatId, userId)) {
            throw new IllegalStateException("Не удалось снять бан в Telegram");
        }
        bannedUserRepo.delete(ban);
    }

    private Long parseNumeric(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Все обращения, свежие сверху. */
    public List<FeedBack> findAllFeedbacks() {
        return feedBackRepo.findAllByOrderByIdDesc();
    }

    public boolean isBannedByFeedback(Long feedbackId) {
        return bannedUserRepo.existsByFeedbackId(feedbackId);
    }

    private String userName(String userId) {
        if (isBlank(userId)) {
            return "";
        }
        return logRepo.findUserNameByUserId(userId).stream().findFirst().orElse("");
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.trim().isEmpty();
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.FEEDBACK.getCommand())) {
            messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
            messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                    "Связаться с администратором",
                    "Сообщить о проблеме или нарушении",
                    "https://api.zhendozzz.ru/app/feedback",
                    messageDTO.getChatId()));
        }
    }
}
