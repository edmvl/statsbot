package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateChatServiceTest {

    private final LogRepo logRepo = mock(LogRepo.class);
    private final LogService logService = mock(LogService.class);
    private final PrivateChatService service = new PrivateChatService(logRepo, logService);

    @Test
    void acceptsOnlyPositiveNumericChatIds() {
        assertThat(service.normalizeChatId("123")).isEqualTo("123");
        assertThat(service.normalizeChatId(" 42 ")).isEqualTo("42");
        assertThat(service.normalizeChatId("-100")).isNull();
        assertThat(service.normalizeChatId("abc")).isNull();
        assertThat(service.normalizeChatId("1.5")).isNull();
        assertThat(service.normalizeChatId(null)).isNull();
    }

    @Test
    void titleFallsBackToUserId() {
        when(logService.findChatName("123")).thenReturn(null);
        when(logRepo.findUserNameByUserId("123")).thenReturn(List.of());

        assertThat(service.findTitle("123")).isEqualTo("ID 123");
    }

    @Test
    void titleUsesKnownUserName() {
        when(logService.findChatName("123")).thenReturn(null);
        when(logRepo.findUserNameByUserId("123")).thenReturn(List.of("Иван Петров (ivan)"));

        assertThat(service.findTitle("123")).isEqualTo("Иван Петров (ivan)");
    }

    @Test
    void messagesComeFromChatLog() {
        ChatMessageDto message = mock(ChatMessageDto.class);
        when(logService.findByChatId("123", 0, 30)).thenReturn(List.of(message));

        assertThat(service.findMessages("123", 0, 30)).containsExactly(message);
    }
}
