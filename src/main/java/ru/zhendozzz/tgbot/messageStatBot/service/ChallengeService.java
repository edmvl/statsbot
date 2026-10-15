package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Challenge;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChallengeReg;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChallengeRegRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChallengeRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ChallengeService implements CommandExecutable {

    private final ChallengeRepo challengeRepo;
    private final ChallengeRegRepo challengeRegRepo;
    private final LogRepo logRepo;
    private final MessageExecutor messageExecutor;

    public ChallengeService(ChallengeRepo challengeRepo, ChallengeRegRepo challengeRegRepo, LogRepo logRepo, MessageExecutor messageExecutor) {
        this.challengeRepo = challengeRepo;
        this.challengeRegRepo = challengeRegRepo;
        this.logRepo = logRepo;
        this.messageExecutor = messageExecutor;
    }

    public void start(
            Integer messageId, String chatId, String chatName, LocalDateTime dateTimeStart, LocalDateTime dateTimeEnd, String description
    ) {
        Challenge existed = challengeRepo.findByChatIdAndDateTimeEndAfterAndFinished(chatId, LocalDateTime.now(), false);
        if (Objects.nonNull(existed)) {
            messageExecutor.sendMessage(chatId, "Новое событие можно запустить только после завершения текущих", messageId);
            return;
        }

        Challenge challenge = new Challenge();
        challenge.setChatId(chatId);
        challenge.setChatName(chatName);
        challenge.setDateTimeStart(dateTimeStart);
        challenge.setDateTimeEnd(dateTimeEnd);
        challenge.setDescription(description);
        challenge.setFinished(false);
        Challenge saved = challengeRepo.save(challenge);

        messageExecutor.sendMessage(
                chatId,
                description + " запущен, для регистрации введите " + BotCommands.CHALLANGE_REGISTRATION.getCommand() + saved.getId(),
                messageId
        );
        if (Objects.nonNull(messageId)) {
            messageExecutor.deleteMessage(Long.valueOf(chatId), messageId);
        }
    }

    public void reg(String challengeId, Long userId, String userName, Integer messageId, Long chatId) {
        ChallengeReg existed = challengeRegRepo.findByChallengeIdAndAndUserId(Long.valueOf(challengeId), userId.toString());
        if (Objects.nonNull(existed)) {
            messageExecutor.sendMessage(chatId, "Вы уже зарегистрированы", messageId);
            return;
        }
        ChallengeReg challengeReg = new ChallengeReg();
        challengeReg.setChallengeId(Long.parseLong(challengeId));
        challengeReg.setUserId(userId.toString());
        challengeReg.setUserName(userName);
        challengeReg.setUserName(userName);
        challengeRegRepo.save(challengeReg);
        messageExecutor.sendMessage(chatId, "Вы зарегистрированы", messageId);
    }

    public void finish(String chatId) {
        Challenge nearest = challengeRepo.findByChatIdAndDateTimeEndLessThanAndFinished(chatId, LocalDateTime.now(), false);
        if (Objects.isNull(nearest)) {
            return;
        }
        messageExecutor.sendMessage(chatId, "Закончился " + nearest.getDescription());
        nearest.setFinished(true);
        challengeRepo.save(nearest);
        List<ChallengeReg> regs = challengeRegRepo.findByChallengeId(nearest.getId());
        if (Objects.isNull(regs) || regs.isEmpty()) {
            return;
        }
        int random = (int) (Math.random() * regs.size());
        ChallengeReg challengeReg = regs.get(random);
        String userNames = String.join(",", logRepo.findUserNameByUserId(challengeReg.getUserId()));
        User user = new User(Long.valueOf(challengeReg.getUserId()), userNames, false);
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        SendMessage sendMessage = new SendMessage();
        String text = "Победитель: ";
        sendMessage.setChatId(chatId);
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setUser(user);
        messageEntity.setOffset(text.length());
        String userIdentityText = firstName + " " + (Objects.nonNull(lastName) ? lastName : "") + "\n";
        text += userIdentityText;
        messageEntity.setLength(userIdentityText.length());
        messageEntity.setType("text_mention");
        sendMessage.setEntities(List.of(messageEntity));
        sendMessage.setText(text);
        messageExecutor.sendMessage(sendMessage);
    }

    public void finishAll() {
        List<String> strings = challengeRepo.findAll().stream().map(Challenge::getChatId).distinct().toList();
        strings.forEach(this::finish);
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.CHALLANGE_REGISTRATION.getCommand())) {
            String[] s = messageDTO.getUserText().split(BotCommands.CHALLANGE_REGISTRATION.getCommand());
            if (s.length >= 1) {
                reg(s[1], messageDTO.getUserId(), messageDTO.getUserName(), messageDTO.getMessageId(), messageDTO.getChatId());
            }
        }
    }
}
