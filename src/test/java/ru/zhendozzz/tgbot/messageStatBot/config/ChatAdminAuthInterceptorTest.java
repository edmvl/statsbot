package ru.zhendozzz.tgbot.messageStatBot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import ru.zhendozzz.tgbot.messageStatBot.service.TelegramWebAppAuthService;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.TelegramRequest;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAdminAuthInterceptorTest {

    private final TelegramWebAppAuthService authService = mock(TelegramWebAppAuthService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final ChatAdminAuthInterceptor interceptor =
            new ChatAdminAuthInterceptor(new AdminAuthGuard(authService, settingsService));

    @Test
    void servesBootstrapWhenNoCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/chats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertTrue(response.getContentAsString().contains(TelegramRequest.AUTH_COOKIE));
    }

    @Test
    void allowsSuperAdminToAnyChat() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(AccessConstants.SUPER_ADMIN_ID);
        MockHttpServletRequest request = requestWithCookie("valid", "GET", "/app/chat/-100500");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void allowsChatAdminToOwnChat() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(42L);
        when(settingsService.isUserAdmin("-100500", 42L)).thenReturn(true);
        MockHttpServletRequest request = requestWithCookie("valid", "GET", "/app/chat/-100500");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void deniesChatAdminOfAnotherChat() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(42L);
        when(settingsService.isUserAdmin("-100500", 42L)).thenReturn(false);
        MockHttpServletRequest request = requestWithCookie("valid", "GET", "/app/chat/-100500");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsChatListOnlyForChatAdmins() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(42L);
        when(settingsService.isAdminOfAnyChat(42L)).thenReturn(true);
        MockHttpServletRequest request = requestWithCookie("valid", "GET", "/app/chats");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        when(settingsService.isAdminOfAnyChat(42L)).thenReturn(false);
        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, denied, new Object()));
        assertEquals(403, denied.getStatus());
    }

    @Test
    void challengeStartIsCheckedAgainstPostedChatId() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(42L);
        when(settingsService.isUserAdmin("-100500", 42L)).thenReturn(true);
        MockHttpServletRequest request = requestWithCookie("valid", "POST", "/app/challenges/start");
        request.addParameter("chatId", "-100500");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        when(settingsService.isUserAdmin("-100999", 42L)).thenReturn(false);
        MockHttpServletRequest foreign = requestWithCookie("valid", "POST", "/app/challenges/start");
        foreign.addParameter("chatId", "-100999");
        MockHttpServletResponse denied = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(foreign, denied, new Object()));
        assertEquals(403, denied.getStatus());
    }

    private MockHttpServletRequest requestWithCookie(String value, String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setCookies(new Cookie(TelegramRequest.AUTH_COOKIE, value));
        return request;
    }
}
