package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberAdministrator;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberBanned;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberMember;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.TgUser;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserStatusServiceTest {

    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final LogRepo logRepo = mock(LogRepo.class);
    private final UserStatusService service =
            new UserStatusService(messageExecutor, settingsService, userRepo, logRepo);

    @BeforeEach
    void setUp() {
        when(settingsService.findTripChannelId()).thenReturn("-100");
        when(logRepo.findChatNameByChatId("-100")).thenReturn("Попутчики");
    }

    @Test
    void reportsMemberStatus() {
        when(messageExecutor.getUserStatus("-100", 777L)).thenReturn(new ChatMemberMember());

        service.executeCommand(dto(".статус 777", 42L), update(new Message()));

        verify(messageExecutor).sendMessage(eq(-100L), contains("Участник"));
    }

    @Test
    void reportsPermanentBan() {
        ChatMemberBanned banned = new ChatMemberBanned();
        banned.setUntilDate(0);
        when(messageExecutor.getUserStatus("-100", 777L)).thenReturn(banned);

        service.executeCommand(dto(".статус 777", 42L), update(new Message()));

        verify(messageExecutor).sendMessage(eq(-100L), contains("Заблокирован (бессрочно)"));
    }

    @Test
    void resolvesTargetFromReply() {
        User repliedUser = new User();
        repliedUser.setId(888L);
        Message replied = new Message();
        replied.setFrom(repliedUser);
        Message message = new Message();
        message.setReplyToMessage(replied);
        when(messageExecutor.getUserStatus("-100", 888L)).thenReturn(new ChatMemberMember());

        service.executeCommand(dto(".статус", 42L), update(message));

        verify(messageExecutor).getUserStatus("-100", 888L);
    }

    @Test
    void resolvesTargetByUsername() {
        TgUser user = new TgUser();
        user.setId("999");
        user.setUserName("ivan");
        when(userRepo.findFirstByUserNameIgnoreCase("ivan")).thenReturn(Optional.of(user));
        when(messageExecutor.getUserStatus("-100", 999L)).thenReturn(new ChatMemberMember());

        service.executeCommand(dto(".статус @ivan", 42L), update(new Message()));

        verify(messageExecutor).getUserStatus("-100", 999L);
    }

    @Test
    void commandIsAvailableForEveryone() {
        when(messageExecutor.getUserStatus("-100", 777L)).thenReturn(new ChatMemberMember());

        service.executeCommand(dto(".статус 777", 999L), update(new Message()));

        verify(messageExecutor).sendMessage(eq(-100L), contains("Участник"));
    }

    @Test
    void asksForUserWhenNoArgument() {
        service.executeCommand(dto(".статус", 42L), update(new Message()));

        verify(messageExecutor, never()).getUserStatus(anyString(), anyLong());
        verify(messageExecutor).sendMessage(eq(-100L), contains("Укажите пользователя"));
    }

    @Test
    void doesNotRevealOwnerOrAdminStatus() {
        ChatMemberAdministrator admin = mock(ChatMemberAdministrator.class);
        when(admin.getStatus()).thenReturn("administrator");
        when(messageExecutor.getUserStatus("-100", 777L)).thenReturn(admin);

        service.executeCommand(dto(".статус 777", 42L), update(new Message()));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(messageExecutor).sendMessage(eq(-100L), text.capture());
        assertThat(text.getValue())
                .contains("Участник")
                .doesNotContain("Администратор")
                .doesNotContain("Владелец");
    }

    private MessageDTO dto(String text, Long userId) {
        return MessageDTO.builder()
                .chatId(-100L)
                .messageId(5)
                .userId(userId)
                .userText(text)
                .build();
    }

    private Update update(Message message) {
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
