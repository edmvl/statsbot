package ru.zhendozzz.tgbot.messageStatBot.processor;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.handler.InlineMessageHandler;
import ru.zhendozzz.tgbot.messageStatBot.handler.MessageHandler;
import ru.zhendozzz.tgbot.messageStatBot.service.AdPaymentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultProcessorTest {

    private final MessageHandler messageHandler = mock(MessageHandler.class);
    private final DefaultProcessor processor = new DefaultProcessor(
            messageHandler, mock(InlineMessageHandler.class), mock(AdPaymentService.class));

    @Test
    void passesEditedMessageToHandler() {
        // edited_message — отдельный тип апдейта; раньше он терялся до обработчика.
        Update update = new Update();
        Message message = new Message();
        message.setMessageId(5);
        update.setEditedMessage(message);

        processor.process(update);

        verify(messageHandler).choose(update);
    }

    @Test
    void passesRegularMessageToHandler() {
        Update update = new Update();
        update.setMessage(new Message());

        processor.process(update);

        verify(messageHandler).choose(update);
    }

    @Test
    void ignoresUpdatesWithoutMessage() {
        processor.process(new Update());

        verify(messageHandler, never()).choose(any());
    }
}
