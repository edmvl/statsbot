package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.SummaryService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SummaryControllerTest {

    private final SummaryService summaryService = mock(SummaryService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final SummaryController controller = new SummaryController(summaryService, currentUserService);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void forbidsEveryoneButSuperAdmin() throws Exception {
        when(currentUserService.isSuperAdmin(any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.runNow("-100", request, response);

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void redirectsWithResultFlag() throws Exception {
        when(currentUserService.isSuperAdmin(any())).thenReturn(true);
        when(summaryService.summarizeChat(eq("-100"), any(LocalDate.class))).thenReturn(true);

        String view = controller.runNow("-100", request, new MockHttpServletResponse());

        assertThat(view).isEqualTo("redirect:/app/chat-settings/-100?summary=sent");
    }
}
