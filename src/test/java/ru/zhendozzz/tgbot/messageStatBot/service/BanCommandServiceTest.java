package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanCommandServiceTest {

    private final FeedbackService feedbackService = mock(FeedbackService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final BanCommandService service = new BanCommandService(feedbackService, settingsService, messageExecutor);

    @Test
    void bansRepliedUserAndClosesFeedback() {
        when(settingsService.findTripChannelId()).thenReturn("-100");
        when(settingsService.isUserAdmin(-100L, 42L)).thenReturn(true);
        Message command = message(42L, "Админ");
        command.setReplyToMessage(message(777L, "Нарушитель"));
        Update update = new Update();
        update.setMessage(command);

        service.executeCommand(dto(), update);

        verify(feedbackService).banByReply("42", "777", "Текст нарушения");
        verify(messageExecutor).deleteMessage(-100L, 5);
    }

    @Test
    void ignoresNonAdmin() {
        when(settingsService.findTripChannelId()).thenReturn("-100");
        when(settingsService.isUserAdmin(-100L, 42L)).thenReturn(false);
        Message command = message(42L, "Пользователь");
        command.setReplyToMessage(message(777L, "Нарушитель"));
        Update update = new Update();
        update.setMessage(command);

        service.executeCommand(dto(), update);

        verify(feedbackService, never()).banByReply(anyString(), anyString(), anyString());
        verify(messageExecutor, never()).deleteMessage(any(), any());
    }

    @Test
    void asksToReplyWhenCommandHasNoReply() {
        when(settingsService.findTripChannelId()).thenReturn("-100");
        when(settingsService.isUserAdmin(-100L, 42L)).thenReturn(true);
        Update update = new Update();
        update.setMessage(message(42L, "Админ"));

        service.executeCommand(dto(), update);

        verify(messageExecutor).sendMessage(-100L,
                "Ответьте на сообщение пользователя, которого нужно заблокировать.", 5);
        verify(messageExecutor).deleteMessage(-100L, 5);
        verify(feedbackService, never()).banByReply(anyString(), anyString(), anyString());
    }

    private MessageDTO dto() {
        return MessageDTO.builder()
                .chatId(-100L)
                .messageId(5)
                .userId(42L)
                .userText(".бан")
                .build();
    }

    private Message message(Long userId, String name) {
        Message message = new Message();
        message.setMessageId(7);
        message.setDate(1_700_000_000);
        message.setChat(new Chat(-100L, "group"));
        User user = new User();
        user.setId(userId);
        user.setFirstName(name);
        user.setUserName("user" + userId);
        user.setIsBot(false);
        message.setFrom(user);
        if ("Нарушитель".equals(name)) {
            message.setText("Текст нарушения");
        } else {
            message.setText(".бан");
        }
        return message;
    }
}
