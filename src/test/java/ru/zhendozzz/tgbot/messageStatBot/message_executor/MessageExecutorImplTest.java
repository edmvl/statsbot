package ru.zhendozzz.tgbot.messageStatBot.message_executor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberRestricted;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.zhendozzz.tgbot.messageStatBot.app.MyTelegramBot;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MessageExecutorImplTest {

    private final MessageExecutorImpl executor = new MessageExecutorImpl();

    @Test
    void removesTooLongCaptionFromMedia() {
        String shortCaption = "Короткий текст";
        String longCaption = "Очень длинный текст рекламы ".repeat(60).trim();

        assertThat(executor.captionForMedia(shortCaption)).isEqualTo(shortCaption);
        assertThat(executor.captionForMedia(longCaption)).isNull();
    }

    @Test
    void splitsLongTextIntoTelegramSizedChunks() {
        String text = ("Первая строка объявления\nВторая строка с подробностями\n").repeat(200);

        List<String> parts = executor.splitText(text);

        assertThat(parts).hasSizeGreaterThan(1);
        assertThat(parts).allMatch(part -> part.length() <= 4000);
        assertThat(String.join("", parts)).isEqualTo(text);
    }

    @Test
    void treatsTelegramBadRequestAsParticipantLeft() throws TelegramApiException {
        MessageExecutorImpl impl = new MessageExecutorImpl();
        MyTelegramBot bot = mock(MyTelegramBot.class);
        doThrow(new TelegramApiRequestException("Bad Request: PARTICIPANT_ID_INVALID", badRequest()))
                .when(bot).execute(any(GetChatMember.class));
        impl.setMyTelegramBot(bot);

        assertThat(impl.getUserStatusInChat(-100L, 1025753212L)).isEqualTo("left");
    }

    @Test
    void keepsNullWhenTelegramIsUnavailable() throws TelegramApiException {
        MessageExecutorImpl impl = new MessageExecutorImpl();
        MyTelegramBot bot = mock(MyTelegramBot.class);
        doThrow(new TelegramApiException("Connection reset"))
                .when(bot).execute(any(GetChatMember.class));
        impl.setMyTelegramBot(bot);

        assertThat(impl.getUserStatusInChat(-100L, 42L)).isNull();
    }

    @Test
    void treatsRestrictedNonMemberAsLeft() throws TelegramApiException {
        MessageExecutorImpl impl = new MessageExecutorImpl();
        MyTelegramBot bot = mock(MyTelegramBot.class);
        doReturn(ChatMemberRestricted.builder().isMember(false).build())
                .when(bot).execute(any(GetChatMember.class));
        impl.setMyTelegramBot(bot);

        assertThat(impl.getUserStatusInChat(-100L, 42L)).isEqualTo("left");
    }

    @Test
    void restrictsMessageAuthorForOneHour() throws TelegramApiException {
        MessageExecutorImpl impl = new MessageExecutorImpl();
        MyTelegramBot bot = mock(MyTelegramBot.class);
        impl.setMyTelegramBot(bot);

        impl.restrictChatMember(-100L, 42L);

        ArgumentCaptor<RestrictChatMember> captor = ArgumentCaptor.forClass(RestrictChatMember.class);
        verify(bot).execute(captor.capture());
        RestrictChatMember request = captor.getValue();
        // Раньше в userId попадал chatId — автор никогда не ограничивался.
        assertThat(request.getUserId()).isEqualTo(42L);
        assertThat(request.getChatId()).isEqualTo("-100");
        assertThat(request.getPermissions().getCanSendMessages()).isFalse();
        assertThat(request.getUntilDate()).isNotNull();
    }

    @Test
    void detectsDeletedChatOnlyForChatNotFound() {
        assertThat(MessageExecutorImpl.isChatGone(
                new TelegramApiRequestException("Bad Request: chat not found", chatNotFound()))).isTrue();
        assertThat(MessageExecutorImpl.isChatGone(
                new TelegramApiRequestException("Forbidden: bot was blocked by the user", forbidden()))).isFalse();
        assertThat(MessageExecutorImpl.isChatGone(new TelegramApiException("Connection reset"))).isFalse();
    }

    @Test
    void stopsMailingsForDeletedChat() throws TelegramApiException {
        MessageExecutorImpl impl = new MessageExecutorImpl();
        MyTelegramBot bot = mock(MyTelegramBot.class);
        SettingsService settings = mock(SettingsService.class);
        impl.setMyTelegramBot(bot);
        impl.setSettingsService(settings);
        doThrow(new TelegramApiRequestException("Bad Request: chat not found", chatNotFound()))
                .when(bot).execute(any(SendMessage.class));

        impl.sendMessage("-100500", "текст");

        verify(settings).markUnavailable("-100500");
    }

    @Test
    void keepsMailingsForBlockedBot() throws TelegramApiException {
        MessageExecutorImpl impl = new MessageExecutorImpl();
        MyTelegramBot bot = mock(MyTelegramBot.class);
        SettingsService settings = mock(SettingsService.class);
        impl.setMyTelegramBot(bot);
        impl.setSettingsService(settings);
        doThrow(new TelegramApiRequestException("Forbidden: bot was blocked by the user", forbidden()))
                .when(bot).execute(any(SendMessage.class));

        impl.sendMessage("-100500", "текст");

        verify(settings, never()).markUnavailable(any());
    }

    private static ApiResponse<?> badRequest() {
        return response(400, "Bad Request: PARTICIPANT_ID_INVALID");
    }

    private static ApiResponse<?> chatNotFound() {
        return response(400, "Bad Request: chat not found");
    }

    private static ApiResponse<?> forbidden() {
        return response(403, "Forbidden: bot was blocked by the user");
    }

    private static ApiResponse<?> response(int errorCode, String errorDescription) {
        ApiResponse<?> response = new ApiResponse<>();
        ReflectionTestUtils.setField(response, "errorCode", errorCode);
        ReflectionTestUtils.setField(response, "errorDescription", errorDescription);
        return response;
    }
}
