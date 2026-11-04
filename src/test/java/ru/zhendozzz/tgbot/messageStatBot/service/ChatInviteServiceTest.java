package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatInviteRepo;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatInviteServiceTest {

    private final ChatInviteRepo chatInviteRepo = mock(ChatInviteRepo.class);
    private final ChatInviteService service = new ChatInviteService(chatInviteRepo);

    @Test
    void recordsWhoAddedEachNewMember() {
        User inviter = user(1L, "admin");
        User first = user(2L, "first");
        User second = user(3L, "second");
        Message message = message(inviter);
        message.setNewChatMembers(List.of(first, second));

        service.processMessage(MessageDTO.builder().serviceMessage(true).build(), update(message));

        verify(chatInviteRepo).upsert("-100500", "1", "2");
        verify(chatInviteRepo).upsert("-100500", "1", "3");
    }

    @Test
    void doesNotRecordSelfJoinViaLink() {
        User joined = user(2L, "joined");
        Message message = message(joined);
        message.setNewChatMembers(List.of(joined));

        service.processMessage(MessageDTO.builder().serviceMessage(true).build(), update(message));

        verify(chatInviteRepo, never()).upsert(any(), any(), any());
    }

    @Test
    void ignoresOrdinaryMessages() {
        User user = user(1L, "user");
        Message message = message(user);
        message.setNewChatMembers(List.of(user(2L, "other")));

        service.processMessage(MessageDTO.builder().serviceMessage(false).build(), update(message));

        verify(chatInviteRepo, never()).upsert(any(), any(), any());
    }

    private Message message(User from) {
        Message message = new Message();
        message.setMessageId(10);
        message.setDate(1_700_000_000);
        message.setFrom(from);
        Chat chat = new Chat(-100500L, "group");
        chat.setTitle("Попутчики");
        message.setChat(chat);
        return message;
    }

    private Update update(Message message) {
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private User user(Long id, String userName) {
        User user = new User();
        user.setId(id);
        user.setUserName(userName);
        user.setFirstName(userName);
        user.setIsBot(false);
        return user;
    }
}
