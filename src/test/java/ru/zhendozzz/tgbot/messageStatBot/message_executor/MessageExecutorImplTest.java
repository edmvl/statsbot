package ru.zhendozzz.tgbot.messageStatBot.message_executor;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberRestricted;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.zhendozzz.tgbot.messageStatBot.app.MyTelegramBot;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

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

    private static ApiResponse<?> badRequest() {
        ApiResponse<?> response = new ApiResponse<>();
        ReflectionTestUtils.setField(response, "errorCode", 400);
        ReflectionTestUtils.setField(response, "errorDescription", "Bad Request: PARTICIPANT_ID_INVALID");
        return response;
    }
}
