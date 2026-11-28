package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.StickerMessage;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import static org.assertj.core.api.Assertions.assertThat;
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
    private static final long AUTHOR_ID = 555L;
    private static final byte[] PNG = {1, 2, 3};

    private MessageExecutor messageExecutor;
    private ChatAdminService chatAdminService;
    private StickerImageService stickerImageService;
    private StickerSetService stickerSetService;
    private StickerCommandService service;

    @BeforeEach
    void setUp() {
        messageExecutor = mock(MessageExecutor.class);
        chatAdminService = mock(ChatAdminService.class);
        stickerImageService = mock(StickerImageService.class);
        stickerSetService = mock(StickerSetService.class);
        service = new StickerCommandService(messageExecutor, chatAdminService, stickerImageService, stickerSetService);
        when(chatAdminService.isAdmin(anyLong(), anyLong())).thenReturn(false);
        when(chatAdminService.isAdmin(anyLong(), eq(AccessConstants.SUPER_ADMIN_ID))).thenReturn(true);
        when(chatAdminService.chatStatus(anyLong(), anyLong())).thenReturn(null);
        when(stickerImageService.render(any())).thenReturn(PNG);
    }

    @Test
    void ignoresNonAdmins() {
        service.executeCommand(dto(ADMIN_ID), update(reply("текст")));

        verify(stickerImageService, never()).render(any());
        verify(messageExecutor, never()).sendSticker(anyLong(), any(), any());
    }

    @Test
    void asksToReplyWhenThereIsNoSourceMessage() {
        service.executeCommand(dto(AccessConstants.SUPER_ADMIN_ID), update(null));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(stickerImageService, never()).render(any());
    }

    @Test
    void refusesNonTextMessages() {
        Message photo = new Message();
        photo.setMessageId(20);
        service.executeCommand(dto(AccessConstants.SUPER_ADMIN_ID), update(photo));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(stickerImageService, never()).render(any());
    }

    @Test
    void addsStickerAndSendsItAsReply() {
        when(chatAdminService.isAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);
        when(stickerSetService.addSticker(PNG, "🖼"))
                .thenReturn(new StickerSetService.Result("hamoryal_by_hamoryalstatsbot", true, 1, "sticker-file-id"));

        service.executeCommand(dto(ADMIN_ID), update(reply("привет")));

        verify(stickerSetService).addSticker(PNG, "🖼");
        verify(messageExecutor).sendSticker(CHAT_ID, "sticker-file-id", 20);
        verify(stickerSetService).attachToChat(CHAT_ID, "hamoryal_by_hamoryalstatsbot");
    }

    @Test
    void usesEmojiFromSecondParameter() {
        when(chatAdminService.isAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);
        when(stickerSetService.addSticker(PNG, "😍"))
                .thenReturn(new StickerSetService.Result("hamoryal_by_hamoryalstatsbot", false, 2, "sticker-file-id"));

        service.executeCommand(dto(ADMIN_ID, ".стикер 😍"), update(reply("привет")));

        verify(stickerSetService).addSticker(PNG, "😍");
        verify(messageExecutor).sendSticker(CHAT_ID, "sticker-file-id", 20);
    }

    @Test
    void parsesEmojiOrDefaultsIt() {
        assertThat(service.parseEmoji(".стикер")).isEqualTo("🖼");
        assertThat(service.parseEmoji(".стикер   ")).isEqualTo("🖼");
        assertThat(service.parseEmoji(".стикер 😍")).isEqualTo("😍");
        assertThat(service.parseEmoji(".стикер ❤️")).isEqualTo("❤️");
        assertThat(service.parseEmoji(".стикер 🔥🔥")).isEqualTo("🔥🔥");
    }

    @Test
    void refusesTextInsteadOfEmoji() {
        when(chatAdminService.isAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);

        service.executeCommand(dto(ADMIN_ID, ".стикер привет"), update(reply("привет")));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(stickerImageService, never()).render(any());
    }

    @Test
    void putsAuthorStatusAndTimeOnSticker() {
        when(chatAdminService.isAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);
        when(chatAdminService.chatStatus(CHAT_ID, AUTHOR_ID)).thenReturn("administrator");
        when(stickerSetService.addSticker(any(), anyString()))
                .thenReturn(new StickerSetService.Result("hamoryal_by_hamoryalstatsbot", false, 2, "sticker-file-id"));
        Message source = reply("привет");

        StickerMessage sticker = service.toStickerMessage(CHAT_ID, source);

        assertThat(sticker.getAuthorName()).isEqualTo("Иван Петров");
        assertThat(sticker.getStatus()).isEqualTo("админ");
        assertThat(sticker.getTime()).isNotNull();
        assertThat(sticker.getText()).isEqualTo("привет");
    }

    @Test
    void marksChannelPostAsChannel() {
        Message source = reply("пост от канала");
        Chat channel = new Chat(-100L, "channel");
        channel.setTitle("Мой канал");
        source.setSenderChat(channel);

        StickerMessage sticker = service.toStickerMessage(CHAT_ID, source);

        assertThat(sticker.getAuthorName()).isEqualTo("Мой канал");
        assertThat(sticker.getStatus()).isEqualTo("канал");
    }

    @Test
    void reportsTelegramFailureToChat() {
        when(chatAdminService.isAdmin(CHAT_ID, ADMIN_ID)).thenReturn(true);
        when(stickerSetService.addSticker(any(), anyString())).thenThrow(new IllegalStateException("STICKERSET_INVALID"));

        service.executeCommand(dto(ADMIN_ID), update(reply("привет")));

        verify(messageExecutor).sendMessage(eq(CHAT_ID), anyString(), eq(10));
        verify(messageExecutor, never()).sendSticker(anyLong(), any(), any());
    }

    private MessageDTO dto(long userId) {
        return dto(userId, ".стикер");
    }

    private MessageDTO dto(long userId, String text) {
        return MessageDTO.builder()
                .chatId(CHAT_ID)
                .userId(userId)
                .messageId(10)
                .userText(text)
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
        message.setDate(1_700_000_000);
        message.setText(text);
        User author = new User();
        author.setId(AUTHOR_ID);
        author.setFirstName("Иван");
        author.setLastName("Петров");
        author.setIsBot(false);
        message.setFrom(author);
        return message;
    }
}
