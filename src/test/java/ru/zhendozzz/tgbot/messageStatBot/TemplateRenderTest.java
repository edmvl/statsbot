package ru.zhendozzz.tgbot.messageStatBot;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JavaxServletWebApplication;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.DriverReviewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.InterestedViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.SubscriptionGroupDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.TgUser;
import ru.zhendozzz.tgbot.messageStatBot.entity.UserNameHistory;
import ru.zhendozzz.tgbot.messageStatBot.rest.ChatAdminsController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Рендерит затронутые шаблоны через Thymeleaf (с web-контекстом), чтобы поймать
 * ошибки в выражениях, которые не видит компилятор.
 */
class TemplateRenderTest {

    private TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private WebContext webContext() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JavaxServletWebApplication application = JavaxServletWebApplication.buildApplication(servletContext);
        return new WebContext(application.buildExchange(request, response));
    }

    private void baseVars(WebContext context) {
        context.setVariable("chats", Collections.emptyList());
        context.setVariable("members", Collections.emptyList());
        context.setVariable("messages", Collections.emptyList());
        context.setVariable("reviews", Collections.emptyList());
        context.setVariable("history", Collections.emptyList());
        context.setVariable("tripList", Collections.emptyList());
        context.setVariable("subscriptions", Collections.emptyList());
        context.setVariable("groups", Collections.emptyList());
        context.setVariable("currentUserId", "123");
        context.setVariable("ruLocale", Locale.forLanguageTag("ru"));
        context.setVariable("driverId", "123");
        context.setVariable("chatId", "123");
        context.setVariable("chatName", "Чат");
        context.setVariable("q", "");
        context.setVariable("limitReached", false);
        context.setVariable("starsPerDay", 100);
        context.setVariable("maxDays", 30);
        context.setVariable("maxFiles", 3);
        context.setVariable("count", 30);
        context.setVariable("currentPage", 0);
        context.setVariable("nextPage", 1);
        context.setVariable("prevPage", 0);
    }

    @Test
    void rendersTemplatesEmpty() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        engine.process("index", context);
        engine.process("chats", context);
        engine.process("trips", context);
        engine.process("driver-reviews", context);
        engine.process("chat", context);
        engine.process("subscriptions", context);
        engine.process("my-subscriptions", context);
        engine.process("ads", context);
        engine.process("ad-proposal", context);
        engine.process("chat-admins", context);
    }

    @Test
    void rendersTripsAndReviewsWithData() {
        TemplateEngine engine = engine();

        TripViewDto trip = mock(TripViewDto.class);
        when(trip.getUserId()).thenReturn("100");
        when(trip.getFirstName()).thenReturn("Иван");
        when(trip.getLastName()).thenReturn("Петров");
        when(trip.getUserName()).thenReturn("ivan");
        when(trip.getText()).thenReturn("Текст поездки");
        when(trip.getDateTime()).thenReturn(LocalDateTime.of(2026, 10, 15, 14, 30));
        when(trip.getRating()).thenReturn(new BigDecimal("4.50"));
        when(trip.getCnt()).thenReturn("5");
        when(trip.getComments()).thenReturn("Отличный попутчик");
        when(trip.getAvatarPath()).thenReturn(null);

        DriverReviewDto review = mock(DriverReviewDto.class);
        when(review.getId()).thenReturn(1L);
        when(review.getStars()).thenReturn(5);
        when(review.getComment()).thenReturn("Всё супер");
        when(review.getReviewUserId()).thenReturn("200");
        when(review.getFirstName()).thenReturn("Пётр");
        when(review.getLastName()).thenReturn("Сидоров");
        when(review.getUserName()).thenReturn("petr");
        when(review.getAvatarPath()).thenReturn("avatars/200.jpg");

        ChatMessageDto message = mock(ChatMessageDto.class);
        when(message.getId()).thenReturn(1L);
        when(message.getDateTime()).thenReturn(LocalDateTime.now());
        when(message.getUserId()).thenReturn("100");
        when(message.getText()).thenReturn("Привет");
        when(message.getFirstName()).thenReturn("Иван");
        when(message.getLastName()).thenReturn("Петров");
        when(message.getUserName()).thenReturn("ivan");

        TgUser driver = new TgUser();
        driver.setId("100");
        driver.setFirstName("Иван");
        driver.setLastName("Петров");
        driver.setUserName("ivan");
        driver.setAvatarPath("avatars/100.jpg");

        WebContext context = webContext();
        baseVars(context);
        context.setVariable("tripList", List.of(trip));
        context.setVariable("reviews", List.of(review));
        context.setVariable("messages", List.of(message));
        context.setVariable("driver", driver);
        context.setVariable("history", List.of(
                history("Иван", "Петров", "ivan", LocalDateTime.now()),
                history(null, null, "old_nick", LocalDateTime.now().minusDays(3))
        ));

        ChatViewDto chat = mock(ChatViewDto.class);
        when(chat.getChatId()).thenReturn("-100123");
        when(chat.getChatName()).thenReturn("Тестовый чат");
        when(chat.getLastMessage()).thenReturn("14.10.2026 12:00");
        context.setVariable("chats", List.of(chat));

        String tripsHtml = engine.process("trips", context);
        // дата и время на странице поездок — в русском формате
        assertThat(tripsHtml).contains("15 октября 2026, 14:30");

        engine.process("chats", context);
        engine.process("driver-reviews", context);
        engine.process("chat", context);
        engine.process("subscriptions", context);
        engine.process("ads", context);
    }
    @Test
    void rendersChatAdminsWithData() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);
        context.setVariable("members", List.of(
                new ChatAdminsController.ChatAdminView("100", "Иван Петров (ivan)", "avatars/100.jpg", true),
                new ChatAdminsController.ChatAdminView("200", "", null, false)
        ));
        context.setVariable("limitReached", true);

        engine.process("chat-admins", context);
    }

    @Test
    void rendersDriverReviewsWithoutAvatar() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        TgUser driver = new TgUser();
        driver.setId("100");
        driver.setFirstName("Иван");
        driver.setLastName(null);
        driver.setUserName(null);
        driver.setAvatarPath(null);
        context.setVariable("driver", driver);

        DriverReviewDto review = mock(DriverReviewDto.class);
        when(review.getReviewUserId()).thenReturn("200");
        when(review.getFirstName()).thenReturn(null);
        when(review.getLastName()).thenReturn(null);
        when(review.getUserName()).thenReturn(null);
        when(review.getAvatarPath()).thenReturn(null);
        when(review.getComment()).thenReturn(null);
        when(review.getStars()).thenReturn(3);
        context.setVariable("reviews", List.of(review));

        engine.process("driver-reviews", context);

        // и вовсе без водителя
        context.setVariable("driver", null);
        engine.process("driver-reviews", context);
    }

    @Test
    void rendersSubscriptionsWithData() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        InterestedViewDto sub = mock(InterestedViewDto.class);
        when(sub.getUserId()).thenReturn("100");
        when(sub.getInterestId()).thenReturn("200");
        when(sub.getChatId()).thenReturn("-100123");
        when(sub.getUserFirstName()).thenReturn("Иван");
        when(sub.getUserLastName()).thenReturn(null);
        when(sub.getUserName()).thenReturn("ivan");
        when(sub.getUserAvatarPath()).thenReturn("avatars/100.jpg");
        when(sub.getInterestFirstName()).thenReturn(null);
        when(sub.getInterestLastName()).thenReturn(null);
        when(sub.getInterestUserName()).thenReturn(null);
        when(sub.getInterestAvatarPath()).thenReturn(null);

        SubscriptionGroupDto group = new SubscriptionGroupDto(
                "100", "Иван", null, "ivan", "avatars/100.jpg", List.of(sub));
        context.setVariable("groups", List.of(group));
        context.setVariable("subscriptions", List.of(sub));

        engine.process("subscriptions", context);
        engine.process("my-subscriptions", context);
    }

    private UserNameHistory history(String firstName, String lastName, String userName, LocalDateTime changedAt) {
        UserNameHistory entry = new UserNameHistory();
        entry.setUserId("100");
        entry.setFirstName(firstName);
        entry.setLastName(lastName);
        entry.setUserName(userName);
        entry.setChangedAt(changedAt);
        return entry;
    }
}
