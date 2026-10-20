package ru.zhendozzz.tgbot.messageStatBot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.service.CommandExecutable;
import ru.zhendozzz.tgbot.messageStatBot.service.NeedProcessMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MessageHandlerTest {

    private final CommandExecutable commandExecutable = mock(CommandExecutable.class);
    private final NeedProcessMessage needProcessMessage = mock(NeedProcessMessage.class);
    private MessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MessageHandler(List.of(commandExecutable), List.of(needProcessMessage));
        ReflectionTestUtils.setField(handler, "botUsername", "test_bot");
    }

    @Test
    void ignoresBotServiceMessageAboutBan() {
        Message message = message(user(999L, "test_bot", true, "Бот"));
        message.setLeftChatMember(user(42L, "banned_user", false, "Забаненный"));

        handler.choose(update(message));

        verify(needProcessMessage, never()).processMessage(any(), any());
        verify(commandExecutable, never()).executeCommand(any(), any());
    }

    @Test
    void describesAddedChatMember() {
        Message message = message(user(1L, "admin", false, "Иван", "Петров"));
        message.setNewChatMembers(List.of(user(2L, "newbie", false, "Пётр", "Сидоров")));

        handler.choose(update(message));

        MessageDTO dto = captureMessage();
        assertThat(dto.isServiceMessage()).isTrue();
        assertThat(dto.getUserText()).isEqualTo("Добавил(а) в чат: Пётр Сидоров (@newbie)");
        verify(commandExecutable, never()).executeCommand(any(), any());
    }

    @Test
    void describesSelfJoin() {
        User joined = user(2L, "newbie", false, "Пётр", "Сидоров");
        Message message = message(joined);
        message.setNewChatMembers(List.of(joined));

        handler.choose(update(message));

        assertThat(captureMessage().getUserText()).isEqualTo("Вступил(а) в чат");
    }

    @Test
    void keepsOrdinaryMessageAndRunsCommands() {
        Message message = message(user(1L, "admin", false, "Иван", "Петров"));
        message.setText(".топ@test_bot");
        Update update = update(message);

        handler.choose(update);

        MessageDTO dto = captureMessage();
        assertThat(dto.isServiceMessage()).isFalse();
        assertThat(dto.getUserText()).isEqualTo(".топ");
        verify(commandExecutable).executeCommand(dto, update);
    }

    private MessageDTO captureMessage() {
        ArgumentCaptor<MessageDTO> captor = ArgumentCaptor.forClass(MessageDTO.class);
        verify(needProcessMessage).processMessage(captor.capture(), any());
        return captor.getValue();
    }

    private Update update(Message message) {
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private Message message(User from) {
        Message message = new Message();
        message.setMessageId(10);
        message.setDate(1_700_000_000);
        message.setFrom(from);
        Chat chat = new Chat(-100500L, "group");
        chat.setTitle("Тестовый чат");
        message.setChat(chat);
        return message;
    }

    private User user(Long id, String userName, boolean bot, String... names) {
        User user = new User();
        user.setId(id);
        user.setUserName(userName);
        user.setIsBot(bot);
        if (names.length > 0) {
            user.setFirstName(names[0]);
        }
        if (names.length > 1) {
            user.setLastName(names[1]);
        }
        return user;
    }
}
