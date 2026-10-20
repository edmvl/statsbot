package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.zhendozzz.tgbot.messageStatBot.config.AdminAuthGuard;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniAppControllerTest {

    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final MiniAppController controller = new MiniAppController(
            mock(TripService.class), mock(FeedbackService.class), mock(FileLoaderService.class),
            mock(LogService.class), mock(InterestedService.class), settingsService,
            mock(RatingRepo.class), mock(UserRepo.class), mock(UserNameHistoryRepo.class),
            currentUserService, mock(AdminAuthGuard.class));

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
}
