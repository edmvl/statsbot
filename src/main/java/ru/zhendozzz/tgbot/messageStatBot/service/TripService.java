package ru.zhendozzz.tgbot.messageStatBot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Rating;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.RatingRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TripService implements CommandExecutable {

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2999, 12, 31, 23, 59, 59);

    private final MessageExecutor messageExecutor;
    private final LogRepo logRepo;
    private final RatingRepo ratingRepo;

    public TripService(MessageExecutor messageExecutor, LogRepo logRepo, RatingRepo ratingRepo) {
        this.messageExecutor = messageExecutor;
        this.logRepo = logRepo;
        this.ratingRepo = ratingRepo;
    }


    public List<TripViewDto> getTripList(
            int offset,
            int size,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            String author,
            String text
    ) {
        return logRepo.getTripChatWithRating(
                offset,
                size,
                dateFrom == null ? MIN_DATE : dateFrom,
                dateTo == null ? MAX_DATE : dateTo,
                normalizeAuthor(author),
                blankToEmpty(text));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeAuthor(String value) {
        String author = blankToEmpty(value);
        return author.startsWith("@") ? author.substring(1) : author;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (messageDTO.getUserText().equals(BotCommands.TRIP.getCommand())) {
            messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText("Открыть");
            inlineKeyboardButton.setWebApp(WebAppInfo.builder()
                    .url("https://api.zhendozzz.ru/app/trips")
                    .build());
            inlineKeyboardMarkup.setKeyboard(List.of(List.of(inlineKeyboardButton)));
            SendMessage sendMessage = new SendMessage();
            sendMessage.setReplyMarkup(inlineKeyboardMarkup);
            sendMessage.setChatId(messageDTO.getChatId());
            sendMessage.setText("Оставить отзыв о поездке");
            messageExecutor.sendMessage(sendMessage);
        }
    }

    /**
     * Сохраняет отзыв о пользователе.
     *
     * @return {@code false}, если автор пытается оставить отзыв самому себе
     *         (такие отзывы не сохраняются)
     */
    public boolean addRating(String userId, String reviewUserId, String rating, String comment) {
        if (userId != null && Objects.equals(userId, reviewUserId)) {
            return false;
        }
        ratingRepo.save(new Rating(userId, reviewUserId, rating, comment));
        return true;
    }
}
