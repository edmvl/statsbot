package ru.zhendozzz.tgbot.messageStatBot.processor;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.handler.InlineMessageHandler;
import ru.zhendozzz.tgbot.messageStatBot.handler.MessageHandler;
import ru.zhendozzz.tgbot.messageStatBot.service.AdPaymentService;
import org.springframework.stereotype.Component;

@Component
public class DefaultProcessor implements Processor {

    private final MessageHandler messageHandler;
    private final InlineMessageHandler inlineMessageHandler;
    private final AdPaymentService adPaymentService;

    public DefaultProcessor(MessageHandler messageHandler, InlineMessageHandler inlineMessageHandler,
                            AdPaymentService adPaymentService) {
        this.messageHandler = messageHandler;
        this.inlineMessageHandler = inlineMessageHandler;
        this.adPaymentService = adPaymentService;
    }

    @Override
    public void executeMessage(Update update) {
        messageHandler.choose(update);
    }

    @Override
    public void executePreCheckoutQuery(Update update) {
        adPaymentService.confirmPreCheckout(update.getPreCheckoutQuery());
    }

    @Override
    public void executeInlineMessage(Update update) {
        inlineMessageHandler.choose(update);
    }
}
