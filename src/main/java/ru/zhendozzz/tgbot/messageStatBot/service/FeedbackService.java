package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBackChat;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
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

    private final FeedBackRepo feedBackRepo;
    private final LogRepo logRepo;
    private final FeedBackChatRepo feedBackChatRepo;
    private final FileLoaderService fileLoaderService;
    private final MessageExecutor messageExecutor;

    public FeedbackService(
            FeedBackRepo feedBackRepo,
            LogRepo logRepo,
            FeedBackChatRepo feedBackChatRepo,
            FileLoaderService fileLoaderService,
            MessageExecutor messageExecutor
    ) {
        this.feedBackRepo = feedBackRepo;
        this.logRepo = logRepo;
        this.feedBackChatRepo = feedBackChatRepo;
        this.fileLoaderService = fileLoaderService;
        this.messageExecutor = messageExecutor;
    }

    public String processFeedback(String complaint, String userId, List<MultipartFile> files) {
        FeedBack feedBack = new FeedBack();
        feedBack.setUserId(userId);
        String userName = logRepo.findUserNameByUserId(userId).stream().findFirst().orElse("");
        feedBack.setUserName(userName);
        feedBack.setDescription(complaint);
        FeedBack savedFeedback = feedBackRepo.save(feedBack);
        if (files != null && !files.isEmpty()) {
            fileLoaderService.processFiles(files, String.valueOf(feedBack.getId()));
        }

        List<String> tripGroupAdmins = feedBackRepo.getTripGroupAdmins();
        tripGroupAdmins.forEach(s -> {
            messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                    "Посмотреть",
                    String.format("%s отправил жалобу:\n%s",
                            Objects.isNull(userName) || userName.isEmpty() ? "Пользователь" : userName,
                            complaint),
                    "https://api.zhendozzz.ru/app/feedback-chat/" + savedFeedback.getId(),
                    s));
        });
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
        String userName = Objects.nonNull(userId) ? logRepo.findUserNameByUserId(userId).stream().findFirst().orElse("") : "";
        feedBackChat.setUserName(userName);
        feedBackChat.setDateTime(LocalDateTime.now());
        feedBackChatRepo.save(feedBackChat);
        messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                "Посмотреть",
                "Администратор ответил вам по заявке",
                "https://api.zhendozzz.ru/app/feedback-chat/" + feedbackId,
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

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.FEEDBACK.getCommand())) {
            messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
            messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(
                    "Связаться с администратором",
                    "Пожаловаться на пользователя или сообщить о проблеме",
                    "https://api.zhendozzz.ru/app/feedback",
                    messageDTO.getChatId()
            ));
        }
    }
}