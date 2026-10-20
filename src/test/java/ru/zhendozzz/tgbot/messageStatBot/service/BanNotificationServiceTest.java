package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.BannedUser;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.BannedUserRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanNotificationServiceTest {

    private final BannedUserRepo bannedUserRepo = mock(BannedUserRepo.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final LogRepo logRepo = mock(LogRepo.class);
    private final BanNotificationService service =
            new BanNotificationService(bannedUserRepo, messageExecutor, logRepo);

    @Test
    void notifiesBannedUserWithFeedbackLink() {
        BannedUser ban = new BannedUser();
        ban.setUserId("777");
        ban.setChatId("-100");
        ban.setFeedbackId(7L);
        when(bannedUserRepo.findFirstByUserIdOrderByBannedAtDesc("777")).thenReturn(Optional.of(ban));
        when(logRepo.findChatNameByChatId("-100")).thenReturn("Попутчики");

        service.processMessage(MessageDTO.builder().userId(777L).userText("привет").build(), new Update());

        verify(messageExecutor).sendMessage(eq("777"), contains("заблокированы"));
        verify(messageExecutor).sendMessage(eq("777"), contains("/app/feedback-chat/7"));
    }

    @Test
    void staysSilentForOtherUsers() {
        when(bannedUserRepo.findFirstByUserIdOrderByBannedAtDesc("888")).thenReturn(Optional.empty());

        service.processMessage(MessageDTO.builder().userId(888L).userText("привет").build(), new Update());

        verify(messageExecutor, never()).sendMessage(eq("888"), contains("заблокированы"));
    }
}
