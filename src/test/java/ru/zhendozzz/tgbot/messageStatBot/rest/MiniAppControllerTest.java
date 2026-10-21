package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import ru.zhendozzz.tgbot.messageStatBot.config.AdminAuthGuard;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.RatingRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserNameHistoryRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.FeedbackService;
import ru.zhendozzz.tgbot.messageStatBot.service.FileLoaderService;
import ru.zhendozzz.tgbot.messageStatBot.service.InterestedService;
import ru.zhendozzz.tgbot.messageStatBot.service.LogService;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import ru.zhendozzz.tgbot.messageStatBot.service.TripService;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiniAppControllerTest {

    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);
    private final FeedbackService feedbackService = mock(FeedbackService.class);
    private final AdminAuthGuard adminAuthGuard = mock(AdminAuthGuard.class);
    private final MiniAppController controller = new MiniAppController(
            mock(TripService.class), feedbackService, mock(FileLoaderService.class),
            mock(LogService.class), mock(InterestedService.class), settingsService,
            mock(RatingRepo.class), userRepo, mock(UserNameHistoryRepo.class),
            currentUserService, adminAuthGuard, messageExecutor);

    @Test
    void accessReportsChatAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentUserService.id(request)).thenReturn(42L);
        when(settingsService.isAdminOfAnyChat(42L)).thenReturn(true);

        Map<String, Boolean> access = controller.access(request);

        assertThat(access).containsEntry("superAdmin", false).containsEntry("chatAdmin", true);
    }

    @Test
    void accessReportsSuperAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentUserService.id(request)).thenReturn(AccessConstants.SUPER_ADMIN_ID);
        when(settingsService.isAdminOfAnyChat(AccessConstants.SUPER_ADMIN_ID)).thenReturn(false);

        Map<String, Boolean> access = controller.access(request);

        assertThat(access).containsEntry("superAdmin", true).containsEntry("chatAdmin", false);
    }

    @Test
    void accessForUnknownUserIsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentUserService.id(request)).thenReturn(null);

        Map<String, Boolean> access = controller.access(request);

        assertThat(access).containsEntry("superAdmin", false).containsEntry("chatAdmin", false);
    }

    @Test
    void banBlocksUserAndWritesToChat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/chat/-100500/ban");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(currentUserService.id(request)).thenReturn(42L);
        when(userRepo.findById("777")).thenReturn(Optional.empty());

        controller.banUser("-100500", "777", "0", request, response);

        verify(messageExecutor).banUser(-100500L, 777L);
        verify(messageExecutor).sendMessage("-100500",
                "🚫 Пользователь 777 заблокирован в чате администратором.");
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/app/chat/-100500?page=0");
    }

    @Test
    void banDoesNotBlockAdminThemselves() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/chat/-100500/ban");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(currentUserService.id(request)).thenReturn(42L);

        controller.banUser("-100500", "42", null, request, response);

        verify(messageExecutor, never()).banUser(any(Long.class), any(Long.class));
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void banDoesNotBlockSuperAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/chat/-100500/ban");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(currentUserService.id(request)).thenReturn(42L);

        controller.banUser("-100500", String.valueOf(AccessConstants.SUPER_ADMIN_ID), null, request, response);

        verify(messageExecutor, never()).banUser(any(Long.class), any(Long.class));
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void feedbackChatIsNotFoundForUnknownId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(currentUserService.idAsString(request)).thenReturn("42");
        when(feedbackService.findFeedbackOrNull(99L)).thenReturn(null);

        String view = controller.feedbackChat(new ExtendedModelMap(), 99L, request, response);

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void feedbackChatOpensReadOnlyWithoutTelegram() throws Exception {
        // Ссылку из чата открыли в обычном браузере: initData нет, но смотреть можно.
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        FeedBack feedback = new FeedBack();
        feedback.setId(7L);
        feedback.setUserId("100");
        when(currentUserService.idAsString(request)).thenReturn(null);
        when(feedbackService.findFeedbackOrNull(7L)).thenReturn(feedback);
        when(feedbackService.findChatByFeedbackId(7L)).thenReturn(List.of());

        String view = controller.feedbackChat(model, 7L, request, response);

        assertThat(view).isEqualTo("feedback-chat");
        assertThat(model.get("authenticated")).isEqualTo(false);
        assertThat(model.get("canReply")).isEqualTo(false);
        assertThat(model.get("canEditTarget")).isEqualTo(false);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain("Доступ только через Telegram");
        verify(adminAuthGuard, never()).allow(any(), any(), anyBoolean());
    }

    @Test
    void feedbackChatIsReadOnlyForStrangers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        FeedBack feedback = new FeedBack();
        feedback.setId(7L);
        feedback.setUserId("100");
        when(currentUserService.idAsString(request)).thenReturn("999");
        when(feedbackService.findFeedbackOrNull(7L)).thenReturn(feedback);
        when(feedbackService.findChatByFeedbackId(7L)).thenReturn(List.of());
        when(adminAuthGuard.isTripAdmin(request)).thenReturn(false);

        String view = controller.feedbackChat(model, 7L, request, response);

        assertThat(view).isEqualTo("feedback-chat");
        assertThat(model.get("isOwner")).isEqualTo(false);
        assertThat(model.get("canReply")).isEqualTo(false);
        assertThat(model.get("canEditTarget")).isEqualTo(false);
    }
}
