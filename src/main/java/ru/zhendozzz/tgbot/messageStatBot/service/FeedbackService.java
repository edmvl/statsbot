package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.objects.Update;
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
                .append(" отправил жалобу");
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

    public List<FeedBackChat> findChatByFeedbackId(Long feedbackId) {
        return feedBackChatRepo.findByFeedbackId(feedbackId);
    }

    public void addChatByFeedbackId(Long feedbackId, String userId, String text) {
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
        feedBackChatRepo.save(feedBackChat);
        messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                "Посмотреть",
                "Администратор ответил вам по заявке",
                FEEDBACK_URL + feedbackId,
                feedback.getUserId()
        ));
    }

    public FeedBack findFeedbackById(Long id) {
        return feedBackRepo.getById(id);
    }

    /** Обращение по id или {@code null}, если такого нет (без ленивого прокси, который падает в шаблоне). */
    public FeedBack findFeedbackOrNull(Long id) {
        return feedBackRepo.findById(id).orElse(null);
    }

    /** Пользователи канала поездок, на которых можно пожаловаться. */
    public List<ChatMemberViewDto> findComplainableUsers(String query) {
        String chatId = settingsService.findTripChannelId();
        if (isBlank(chatId)) {
            return List.of();
        }
        return logRepo.findChatMembers(chatId, isBlank(query) ? "" : query.trim(), 20);
    }

    /**
     * Блокирует пользователя из жалобы: бан в канале поездок, запись в список
     * заблокированных и сообщение в канал со ссылкой на жалобу.
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
        feedBackRepo.save(feedback);

        messageExecutor.sendMessage(chatId,
                "🚫 Пользователь " + label + " заблокирован по жалобе.\n" + FEEDBACK_URL + feedbackId);
    }

    public List<BannedUser> findAllBans() {
        return bannedUserRepo.findAllByOrderByBannedAtDesc();
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
                    "Пожаловаться на пользователя или сообщить о проблеме",
                    "https://api.zhendozzz.ru/app/feedback",
                    messageDTO.getChatId()));
        }
    }
}
