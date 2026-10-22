package ru.zhendozzz.tgbot.messageStatBot.processor;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface Processor {

    void executeMessage(Update message);

    default void process(Update update) {
        if (update.hasPreCheckoutQuery()) {
            executePreCheckoutQuery(update);
        }

        // edited_message — тоже сообщение: его нужно сохранить как правку.
        if (update.hasMessage() || update.hasEditedMessage()) {
            executeMessage(update);
        }

        if (update.hasInlineQuery()) {
            executeInlineMessage(update);
        }
    }

    /**
     * Ответ на {@code pre_checkout_query} (подтверждение оплаты). По умолчанию ничего не делаем.
     */
    default void executePreCheckoutQuery(Update update) {
    }

    void executeInlineMessage(Update message);
}
