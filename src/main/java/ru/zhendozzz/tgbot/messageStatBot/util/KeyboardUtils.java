package ru.zhendozzz.tgbot.messageStatBot.util;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;

public class KeyboardUtils {
    public static SendMessage getKeyboardMessage(String buttonText, String messageText, String appUrl, String chatId) {
        return getKeyboardMessage(buttonText, messageText, appUrl, Long.valueOf(chatId));
    }

    public static SendMessage getKeyboardMessage(String buttonText, String messageText, String appUrl, Long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText(buttonText);
        inlineKeyboardButton.setWebApp(WebAppInfo.builder()
                .url(appUrl)
                .build());
        inlineKeyboardMarkup.setKeyboard(List.of(List.of(inlineKeyboardButton)));
        SendMessage sendMessage = new SendMessage();
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText);
        return sendMessage;
    }
}
