package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdFilterServiceTest {

    private static final long CHAT_ID = -1001872304461L;
    private static final long AUTHOR_ID = 777L;
    private static final int MESSAGE_ID = 42;
    private static final String AD_TEXT =
            "Лучшие плагины для AyuGram и ExteraGram - @MiozPlugins";

    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final LogService logService = mock(LogService.class);
    private final AdFilterService service = new AdFilterService(messageExecutor, settingsService, logService);

    private void enabled() {
        when(settingsService.isEnabled(CHAT_ID, ChatSettingConstants.ENABLE_AD_FILTER)).thenReturn(true);
    }

    private Update update(String text) {
        Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        message.setText(text);
        message.setEntities(List.of(entity(text, "mention")));
        message.setChat(new Chat(CHAT_ID, "supergroup"));
        message.setFrom(user(false));
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private User user(boolean bot) {
        User user = new User();
        user.setId(AUTHOR_ID);
        user.setIsBot(bot);
        return user;
    }

    private MessageEntity entity(String text, String type) {
        int at = text.indexOf('@');
        MessageEntity entity = new MessageEntity();
        entity.setType(type);
        entity.setOffset(at);
        entity.setLength(text.length() - at);
        return entity;
    }

    private MessageDTO dto() {
        return MessageDTO.builder()
                .chatId(CHAT_ID)
                .messageId(MESSAGE_ID)
                .userId(AUTHOR_ID)
                .userText(AD_TEXT)
                .serviceMessage(false)
                .build();
    }

    private Chat chat(long id, String type) {
        return new Chat(id, type);
    }

    @Test
    void ignoresChatsWithoutSetting() {
        service.processMessage(dto(), update(AD_TEXT));

        verify(messageExecutor, never()).getChat(anyString());
        verify(messageExecutor, never()).deleteMessage(anyLong(), anyInt());
    }

    @Test
    void deletesAdWithChannelMention() {
        enabled();
        when(messageExecutor.getChat("MiozPlugins")).thenReturn(chat(-1001234567890L, "channel"));

        service.processMessage(dto(), update(AD_TEXT));

        verify(messageExecutor).deleteMessage(CHAT_ID, MESSAGE_ID);
        verify(logService).markDeleted(CHAT_ID, MESSAGE_ID);
        verify(messageExecutor).restrictChatMember(CHAT_ID, AUTHOR_ID);
    }

    @Test
    void doesNotMuteBotAuthors() {
        enabled();
        when(messageExecutor.getChat("MiozPlugins")).thenReturn(chat(-1001234567890L, "channel"));
        Update update = update(AD_TEXT);
        update.getMessage().setFrom(user(true));

        service.processMessage(dto(), update);

        verify(messageExecutor).deleteMessage(CHAT_ID, MESSAGE_ID);
        verify(messageExecutor, never()).restrictChatMember(anyLong(), anyLong());
    }

    @Test
    void keepsMessageWithoutAdMarkers() {
        enabled();
        when(messageExecutor.getChat("MiozPlugins")).thenReturn(chat(-1001234567890L, "channel"));

        service.processMessage(dto(), update("Переходите в @MiozPlugins"));

        verify(messageExecutor, never()).deleteMessage(anyLong(), anyInt());
    }

    @Test
    void keepsMentionOfPerson() {
        enabled();
        when(messageExecutor.getChat("MiozPlugins")).thenReturn(chat(777L, "private"));

        service.processMessage(dto(), update(AD_TEXT));

        verify(messageExecutor, never()).deleteMessage(anyLong(), anyInt());
    }

    @Test
    void keepsMentionOfOwnSupergroup() {
        enabled();
        when(messageExecutor.getChat("MiozPlugins")).thenReturn(chat(CHAT_ID, "supergroup"));

        service.processMessage(dto(), update(AD_TEXT));

        verify(messageExecutor, never()).deleteMessage(anyLong(), anyInt());
    }

    @Test
    void keepsMessagesFromAdmins() {
        enabled();
        when(settingsService.isUserAdmin(CHAT_ID, AUTHOR_ID)).thenReturn(true);

        service.processMessage(dto(), update(AD_TEXT));

        verify(messageExecutor, never()).getChat(anyString());
        verify(messageExecutor, never()).deleteMessage(anyLong(), anyInt());
    }

    @Test
    void cachesChannelLookup() {
        enabled();
        when(messageExecutor.getChat("MiozPlugins")).thenReturn(chat(-1001234567890L, "channel"));

        service.processMessage(dto(), update(AD_TEXT));
        service.processMessage(dto(), update(AD_TEXT));

        verify(messageExecutor, times(1)).getChat("MiozPlugins");
        verify(messageExecutor, times(2)).deleteMessage(eq(CHAT_ID), eq(MESSAGE_ID));
    }

    @Test
    void ignoresMessageWithoutMentions() {
        enabled();
        Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        message.setText("Лучшие плагины для AyuGram и ExteraGram");
        message.setChat(new Chat(CHAT_ID, "supergroup"));
        Update update = new Update();
        update.setMessage(message);

        service.processMessage(dto(), update);

        verify(messageExecutor, never()).getChat(anyString());
        verify(messageExecutor, never()).deleteMessage(anyLong(), anyInt());
    }
}
