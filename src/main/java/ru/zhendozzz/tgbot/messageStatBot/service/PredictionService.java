package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.Prediction;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.PredictionRepository;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.util.Random;

@Service
public class PredictionService implements CommandExecutable {

    private final PredictionRepository predictionRepository;

    private final MessageExecutor messageExecutor;

    public PredictionService(PredictionRepository predictionRepository, MessageExecutor messageExecutor) {
        this.predictionRepository = predictionRepository;
        this.messageExecutor = messageExecutor;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().startsWith(BotCommands.PREDICTION.getCommand())) {
            Random rand = new Random();
            int i = rand.nextInt((int) predictionRepository.count());
            Prediction predictionById = predictionRepository.getPredictionById(i);
            messageExecutor.sendMessage(messageDTO.getChatId(), predictionById.getText(), messageDTO.getMessageId());
        }
    }
}
