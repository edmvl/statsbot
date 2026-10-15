package ru.zhendozzz.tgbot.messageStatBot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.zhendozzz.tgbot.messageStatBot.app.MyTelegramBot;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class InlineMessageHandler implements Handler<Update> {
    private MyTelegramBot tgBot;
    private final LogRepo logRepo;

    public InlineMessageHandler(LogRepo logRepo) {
        this.logRepo = logRepo;
    }

    @Override
    public void choose(Update update) {
        InlineQuery message = update.getInlineQuery();
        String query = message.getQuery();
        if (query.isEmpty()) {
            return;
        }
        List<InlineQueryResultArticle> list = Arrays.stream(BotCommands.values()).map(command -> InlineQueryResultArticle.builder()
                .id(command.getCommand())
                .title(command.getCommand())
                .description(command.getExplainer())
                .inputMessageContent(InputTextMessageContent.builder()
                        .messageText(command.getCommand())
                        .build())
                .build()
        ).filter(Objects::nonNull).toList();

        List<InlineQueryResult> results = new ArrayList<>(list);
        AnswerInlineQuery answer = new AnswerInlineQuery();
        answer.setInlineQueryId(message.getId());
        answer.setResults(results);
        answer.setCacheTime(1); // Время кэширования в секундах
        try {
            tgBot.execute(answer);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Autowired
    public void setMyTelegramBot(MyTelegramBot tgBot) {
        this.tgBot = tgBot;
    }
}
