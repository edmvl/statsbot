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

class SuperAdminAuthInterceptorTest {

    private final TelegramWebAppAuthService authService = mock(TelegramWebAppAuthService.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final SuperAdminAuthInterceptor interceptor =
            new SuperAdminAuthInterceptor(new AdminAuthGuard(authService, settingsService));

    @Test
    void servesBootstrapWhenNoCookie() throws Exception {        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/chat-settings/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains(TelegramRequest.AUTH_COOKIE));
    }

    @Test
    void allowsSuperAdmin() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(AccessConstants.SUPER_ADMIN_ID);
        MockHttpServletRequest request = requestWithCookie("valid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void deniesNonSuperAdmin() throws Exception {
        when(authService.getVerifiedUserId("valid")).thenReturn(42L);
        MockHttpServletRequest request = requestWithCookie("valid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void deniesInvalidInitData() throws Exception {
        when(authService.getVerifiedUserId("forged")).thenReturn(null);
        MockHttpServletRequest request = requestWithCookie("forged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    private MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/chat-settings/1");
        request.setCookies(new Cookie(TelegramRequest.AUTH_COOKIE, value));
        return request;
    }
}
