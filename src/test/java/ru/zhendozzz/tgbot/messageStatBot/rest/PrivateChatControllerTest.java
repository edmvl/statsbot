package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import ru.zhendozzz.tgbot.messageStatBot.dto.PrivateChatDto;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.PrivateChatService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateChatControllerTest {

    private final PrivateChatService service = mock(PrivateChatService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final PrivateChatController controller = new PrivateChatController(service, currentUserService);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void listsPrivateChats() throws Exception {
        when(currentUserService.isSuperAdmin(any())).thenReturn(true);
        PrivateChatDto chat = mock(PrivateChatDto.class);
        when(service.findChats()).thenReturn(List.of(chat));
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.chats(request, response, model);

        assertThat(view).isEqualTo("dms");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("chats")).isEqualTo(List.of(chat));
    }

    @Test
    void opensConversationWithPaging() throws Exception {
        when(currentUserService.isSuperAdmin(any())).thenReturn(true);
        when(service.normalizeChatId("123")).thenReturn("123");
        when(service.findMessages("123", 2, 50)).thenReturn(List.of());
        when(service.findTitle("123")).thenReturn("Иван (ivan)");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.chat("123", "2", "50", request, response, model);

        assertThat(view).isEqualTo("dm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("chatId")).isEqualTo("123");
        assertThat(model.get("chatTitle")).isEqualTo("Иван (ivan)");
        assertThat(model.get("currentPage")).isEqualTo(2);
        assertThat(model.get("count")).isEqualTo(50);
        assertThat(model.get("prevPage")).isEqualTo(1);
        assertThat(model.get("nextPage")).isEqualTo(3);
    }

    @Test
    void rejectsNonPrivateChatId() throws Exception {
        when(currentUserService.isSuperAdmin(any())).thenReturn(true);
        when(service.normalizeChatId("-100")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.chat("-100", null, null, request, response, new ExtendedModelMap());

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void deniesEveryoneButSuperAdmin() throws Exception {
        when(currentUserService.isSuperAdmin(any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.chats(request, response, new ExtendedModelMap());

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
