package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuizChatDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuizDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuizChat;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuizChatRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuizRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Random;

@Service
public class QuizService implements CommandExecutable, NeedProcessMessage {
    private final MessageExecutor messageExecutor;

    private final QuizRepo quizRepo;
    private final QuizChatRepo quizChatRepo;
    private final SettingsService settingsService;

    public QuizService(MessageExecutor messageExecutor, QuizRepo quizRepo, QuizChatRepo quizChatRepo, SettingsService settingsService) {
        this.messageExecutor = messageExecutor;
        this.quizRepo = quizRepo;
        this.quizChatRepo = quizChatRepo;
        this.settingsService = settingsService;
    }

    public void sendQuestion(String chatId) {
        List<QuizDto> notAskedQuizzesByChatId = quizRepo.findNotAskedQuizzesByChatId(chatId);
        QuizDto quizDto = notAskedQuizzesByChatId.stream().skip(
                new Random().nextInt(notAskedQuizzesByChatId.size())
        ).findFirst().get();
        Integer messageId = messageExecutor.sendMessage(chatId, quizDto.getQuestion() + "(" + quizDto.getAsk().length() + " букв)");
        QuizChat quizChat = new QuizChat();
        quizChat.setChatId(chatId);
        quizChat.setQuestionId(quizDto.getId());
        quizChat.setMessageId(messageId);
        quizChatRepo.save(quizChat);
    }

    private void checkAsk(Long chatId, Integer messageId, Integer replyMessageId, Long userId, String text) {
        QuizChatDto lastQuizForChat = quizRepo.findLastQuizForChat(String.valueOf(chatId));
        if (Objects.isNull(lastQuizForChat)) {
            return;
        }
        Long id = lastQuizForChat.getId();
        if (Objects.isNull(id)) {
            return;
        }
        if (!lastQuizForChat.getMessageId().equals(replyMessageId)) {
            return;
        }
        String ask = lastQuizForChat.getAsk();
        QuizChat byId = quizChatRepo.findById(id).get();
        if (ask.equalsIgnoreCase(text)) {
            byId.setWinnerId(userId.toString());
            messageExecutor.sendMessage(chatId, "Верно!", messageId);
            messageExecutor.sendMessage(chatId, "Следующий вопрос:");
            sendQuestion(chatId.toString());
        } else {
            Integer attempt = byId.getAttempt();
            attempt = Objects.nonNull(attempt) ? ++attempt : 1;
            byId.setAttempt(attempt);
            String replyText = ask.substring(0, attempt) + ".".repeat(ask.length() - attempt);
            messageExecutor.sendMessage(chatId, "Не верно, подсказка: " + replyText, messageId);
        }
        quizChatRepo.save(byId);
    }

    @Override
    public void executeCommand(MessageDTO dto, Update update) {
        if (dto.getUserText().startsWith(BotCommands.QUIZ.getCommand()) && settingsService.isUserAdmin(dto.getChatId(), dto.getUserId())) {
            messageExecutor.sendMessage(dto.getChatId(), "Викторина запущена!\nОтвечать необходимо ответом на сообщение с вопросом, инпче ответ не засчитается");
            sendQuestion(dto.getChatId().toString());
            messageExecutor.deleteMessage(dto.getChatId(), dto.getMessageId());
        }
    }

    @Override
    public void processMessage(MessageDTO dto, Update update) {
        if (dto.isServiceMessage()) {
            return;
        }
        checkAsk(dto.getChatId(), dto.getMessageId(), dto.getReplyMessageId(), dto.getUserId(), dto.getUserText());
    }
}
