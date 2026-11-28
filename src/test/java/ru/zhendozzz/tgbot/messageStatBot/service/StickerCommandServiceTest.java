package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StickerCommandServiceTest {

    private static final long CHAT_ID = -100500L;
    private static final long ADMIN_ID = 777L;
    private static final byte[] PNG = {1, 2, 3};

    private MessageExecutor messageExecutor;
    private SettingsService settingsService;
    private StickerImageService stickerImageService;
    private StickerSetService stickerSetService;
    private StickerCommandService service;

    @BeforeEach
    void setUp() {
        messageExecutor = mock(MessageExecutor.class);
        settingsService = mock(SettingsService.class);
        stickerImageService = mock(StickerImageService.class);
        stickerSetService = mock(StickerSetService.class);
        service = new StickerCommandService(messageExecutor, settingsService, stickerImageService, stickerSetService);
        when(settingsService.isUserAdmin(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void ignoresNonAdmins() {
        service.executeCommand(dto(ADMIN_ID), update(reply("текст")));

        verify(stickerImageService, never()).render(anyString());
        verify(messageExecutor, never()).sendSticker(anyLong(), any(), any());
    }

    @Test
    void asksToReplyWhenThereIsNoSourceMessage() {
        service.executeCommand(dto(AccessConstants.SUPER_ADMIN_ID), update(null));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(stickerImageService, never()).render(anyString());
    }

    @Test
    void refusesNonTextMessages() {
        Message photo = new Message();
        photo.setMessageId(20);
        service.executeCommand(dto(AccessConstants.SUPER_ADMIN_ID), update(photo));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(stickerImageService, never()).render(anyString());
    }

    @Test
    void addsStickerAndSendsItAsReply() {
        when(settingsService.isUserAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);
        when(stickerImageService.render("привет")).thenReturn(PNG);
        when(stickerSetService.addSticker(PNG))
                .thenReturn(new StickerSetService.Result("hamoryal_by_hamoryalstatsbot", true, 1, "sticker-file-id"));

        service.executeCommand(dto(ADMIN_ID), update(reply("привет")));

        verify(stickerSetService).addSticker(PNG);
        verify(messageExecutor).sendSticker(CHAT_ID, "sticker-file-id", 20);
        verify(stickerSetService).attachToChat(CHAT_ID, "hamoryal_by_hamoryalstatsbot");
    }

    @Test
    void reportsTelegramFailureToChat() {
        when(settingsService.isUserAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);
        when(stickerImageService.render("привет")).thenReturn(PNG);
        when(stickerSetService.addSticker(PNG)).thenThrow(new IllegalStateException("STICKERSET_INVALID"));

        service.executeCommand(dto(ADMIN_ID), update(reply("привет")));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(messageExecutor, never()).sendSticker(anyLong(), any(), any());
    }

    private MessageDTO dto(long userId) {
        return MessageDTO.builder()
                .chatId(CHAT_ID)
                .userId(userId)
                .messageId(10)
                .userText(".стикер")
                .build();
    }

    private Update update(Message reply) {
        Message command = new Message();
        command.setMessageId(10);
        command.setText(".стикер");
        command.setReplyToMessage(reply);
        Update update = new Update();
        update.setMessage(command);
        return update;
    }

    private Message reply(String text) {
        Message message = new Message();
        message.setMessageId(20);
        message.setText(text);
        return message;
    }
}
