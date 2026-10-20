package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.zhendozzz.tgbot.messageStatBot.entity.BannedUser;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.BannedUserRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.FeedBackChatRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.FeedBackRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

    private final FeedBackRepo feedBackRepo = mock(FeedBackRepo.class);
    private final LogRepo logRepo = mock(LogRepo.class);
    private final FeedBackChatRepo feedBackChatRepo = mock(FeedBackChatRepo.class);
    private final FileLoaderService fileLoaderService = mock(FileLoaderService.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final BannedUserRepo bannedUserRepo = mock(BannedUserRepo.class);
    private final SettingsService settingsService = mock(SettingsService.class);

    private final FeedbackService service = new FeedbackService(
            feedBackRepo, logRepo, feedBackChatRepo, fileLoaderService,
            messageExecutor, bannedUserRepo, settingsService);

    @BeforeEach
    void setUp() {
        when(settingsService.findTripChannelId()).thenReturn("-100");
        when(logRepo.findUserNameByUserId("777")).thenReturn(List.of("Иван Петров (@ivan)"));
    }

    @Test
    void processFeedbackStoresTargetUser() {
        when(logRepo.findUserNameByUserId("42")).thenReturn(List.of("Пётр"));
        when(feedBackRepo.save(any(FeedBack.class))).thenAnswer(invocation -> {
            FeedBack feedback = invocation.getArgument(0);
            feedback.setId(9L);
            return feedback;
        });
        when(feedBackRepo.getTripGroupAdmins()).thenReturn(List.of());

        service.processFeedback("текст жалобы", "42", "777", List.of());

        ArgumentCaptor<FeedBack> captor = ArgumentCaptor.forClass(FeedBack.class);
        verify(feedBackRepo).save(captor.capture());
        assertThat(captor.getValue().getTargetUserId()).isEqualTo("777");
        assertThat(captor.getValue().getTargetUserName()).isEqualTo("Иван Петров (@ivan)");
        assertThat(captor.getValue().getUserId()).isEqualTo("42");
    }

    @Test
    void banBlocksUserAndWritesToTripChannel() {
        FeedBack feedback = feedback(7L, "777");
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback));
        when(bannedUserRepo.existsByUserIdAndChatId("777", "-100")).thenReturn(false);

        service.banForFeedback(7L, "42");

        verify(messageExecutor).banUser("-100", "777");
        ArgumentCaptor<BannedUser> captor = ArgumentCaptor.forClass(BannedUser.class);
        verify(bannedUserRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("777");
        assertThat(captor.getValue().getFeedbackId()).isEqualTo(7L);
        assertThat(captor.getValue().getBannedBy()).isEqualTo("42");

        verify(messageExecutor).sendMessage(eq("-100"), contains("заблокирован по жалобе"));
        assertThat(feedback.getFinished()).isTrue();
        verify(feedBackRepo).save(feedback);
    }

    @Test
    void banIsIdempotentForSameChat() {
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback(7L, "777")));
        when(bannedUserRepo.existsByUserIdAndChatId("777", "-100")).thenReturn(true);

        service.banForFeedback(7L, "42");

        verify(messageExecutor, never()).banUser(anyString(), anyString());
        verify(bannedUserRepo, never()).save(any(BannedUser.class));
    }

    @Test
    void banWithoutTargetUserIsRejected() {
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback(7L, null)));

        assertThatThrownBy(() -> service.banForFeedback(7L, "42"))
                .isInstanceOf(IllegalStateException.class);

        verify(messageExecutor, never()).banUser(anyString(), anyString());
    }

    @Test
    void closeFeedbackSavesReasonAndNotifiesAuthor() {
        FeedBack feedback = feedback(7L, "777");
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback));

        service.closeFeedback(7L, "Недостаточно доказательств", "42");

        assertThat(feedback.getFinished()).isTrue();
        assertThat(feedback.getCloseReason()).isEqualTo("Недостаточно доказательств");
        assertThat(feedback.getClosedAt()).isNotNull();
        verify(feedBackRepo).save(feedback);
        verify(messageExecutor).sendMessage(any(SendMessage.class));
    }

    @Test
    void closeFeedbackRequiresReason() {
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback(7L, "777")));

        assertThatThrownBy(() -> service.closeFeedback(7L, "  ", "42"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reopenClearsCloseReason() {
        FeedBack feedback = feedback(7L, "777");
        feedback.setFinished(true);
        feedback.setCloseReason("Недостаточно доказательств");
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback));
        when(feedBackRepo.getTripGroupAdmins()).thenReturn(List.of());

        service.reopenFeedback(7L, "42");

        assertThat(feedback.getFinished()).isFalse();
        assertThat(feedback.getCloseReason()).isNull();
    }

    @Test
    void reopenRejectsForeignComplaint() {
        when(feedBackRepo.findById(7L)).thenReturn(Optional.of(feedback(7L, "777")));

        assertThatThrownBy(() -> service.reopenFeedback(7L, "999"))
                .isInstanceOf(IllegalStateException.class);
    }

    private FeedBack feedback(Long id, String targetUserId) {
        FeedBack feedback = new FeedBack();
        feedback.setId(id);
        feedback.setUserId("42");
        feedback.setTargetUserId(targetUserId);
        feedback.setDescription("жалоба");
        return feedback;
    }
}
