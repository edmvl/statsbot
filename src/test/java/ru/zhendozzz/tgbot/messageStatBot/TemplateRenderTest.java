package ru.zhendozzz.tgbot.messageStatBot;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring5.SpringTemplateEngine;
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
import ru.zhendozzz.tgbot.messageStatBot.entity.BannedUser;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;
import ru.zhendozzz.tgbot.messageStatBot.entity.LogEdit;
import ru.zhendozzz.tgbot.messageStatBot.entity.Quest;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestCheckpoint;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestProgress;
import ru.zhendozzz.tgbot.messageStatBot.entity.UserNameHistory;
import ru.zhendozzz.tgbot.messageStatBot.rest.ChatAdminsController;
import ru.zhendozzz.tgbot.messageStatBot.rest.ChatSettingsController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        // SpringTemplateEngine использует SpEL — так тесты ловят ошибки вида EL1001E
        // (обычный TemplateEngine работает на OGNL и такие места молча пропускает).
        SpringTemplateEngine engine = new SpringTemplateEngine();
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
    void rendersTargetUserEditorOnlyForAuthorAndAdmin() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        FeedBack feedback = new FeedBack();
        feedback.setId(1L);
        feedback.setUserId("100");
        feedback.setUserName("Иван Петров");
        feedback.setDescription("Не пришёл на встречу");
        context.setVariable("feedback", feedback);
        context.setVariable("feedBackChatList", List.of());
        context.setVariable("files", List.of());
        context.setVariable("commentFiles", Map.of());
        context.setVariable("banned", false);
        context.setVariable("isAdmin", false);
        context.setVariable("isOwner", true);

        // Автор: поле выбора доступно, комментарий можно оставить.
        context.setVariable("authenticated", true);
        context.setVariable("canEditTarget", true);
        context.setVariable("canReply", true);
        String editor = engine.process("feedback-chat", context);
        assertThat(editor)
                .contains("id=\"targetSearch\"")
                .contains("/app/feedback-chat/1/target")
                .contains("class=\"reply-form\"")
                .contains("data-back=\"/app/feedback\"")
                // Элемент для подписи выбранного пользователя должен быть в DOM всегда,
                // иначе JS падал на selected.textContent и не включал кнопку «Сохранить».
                .contains("id=\"selectedUser\"")
                .contains("Пользователь, на которого жалоба, не указан");

        // Остальные (в том числе тот, на кого жалуются) — смотрят, но сделать ничего не могут.
        feedback.setTargetUserId("777");
        feedback.setTargetUserName("Иван Петров (@ivan)");
        context.setVariable("authenticated", false);
        context.setVariable("canEditTarget", false);
        context.setVariable("canReply", false);
        context.setVariable("isAdmin", false);
        context.setVariable("isOwner", false);
        context.setVariable("isTarget", true);
        String readOnly = engine.process("feedback-chat", context);
        assertThat(readOnly)
                .doesNotContain("id=\"targetSearch\"")
                .doesNotContain("/app/feedback-chat/1/target")
                .doesNotContain("class=\"reply-form\"")
                .doesNotContain("class=\"close-form\"")
                .doesNotContain("/app/feedback-chat/1/ban")
                // Без initData (ссылка открыта в браузере) кнопки «Назад» нет.
                .doesNotContain("data-back=")
                .contains("<h2>Переписка</h2>")
                .contains("Иван Петров (@ivan)")
                .contains("Вы указаны в этой жалобе");
    }

    @Test
    void rendersIndexWithoutAdminAttributes() {
        // Так index.html рендерится как welcome-page для "/" — с пустой моделью.
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        String html = engine.process("index", context);

        assertThat(html).contains("Урмарский помощник");
    }

    @Test
    void rendersAdminAndFeedbackPages() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        context.setVariable("settings", List.of(
                new ChatSettingsController.ChatSettingView("enable_ads", true),
                new ChatSettingsController.ChatSettingView("enable_stats", false),
                new ChatSettingsController.ChatSettingView("trip_channel", true)));

        FeedBack feedback = new FeedBack();
        feedback.setId(1L);
        feedback.setUserId("100");
        feedback.setUserName("Иван Петров");
        feedback.setDescription("Не пришёл на встречу");
        context.setVariable("feedback", feedback);
        context.setVariable("feedBackChatList", List.of());
        context.setVariable("files", List.of("screenshot.jpg"));
        context.setVariable("commentFiles", Map.of());
        context.setVariable("isAdmin", true);
        context.setVariable("isOwner", false);

        engine.process("chat-settings", context);
        engine.process("feedback", context);
        engine.process("feedback-chat", context);

        // Жалоба с указанным пользователем: кнопка «Заблокировать», затем состояние «заблокирован».
        feedback.setTargetUserId("777");
        feedback.setTargetUserName("Иван Петров (@ivan)");
        context.setVariable("banned", false);
        engine.process("feedback-chat", context);
        context.setVariable("banned", true);
        engine.process("feedback-chat", context);

        // Закрытая жалоба глазами автора: причина отказа и кнопка «Переоткрыть».
        feedback.setFinished(true);
        feedback.setCloseReason("Недостаточно доказательств");
        context.setVariable("isAdmin", false);
        context.setVariable("isOwner", true);
        context.setVariable("banned", false);
        engine.process("feedback-chat", context);

        BannedUser ban = new BannedUser();
        ban.setId(1L);
        ban.setUserId("777");
        ban.setUserName("Иван Петров");
        ban.setFeedbackId(1L);
        ban.setBannedBy("42");
        ban.setBannedAt(LocalDateTime.now());
        context.setVariable("bans", List.of(ban));
        engine.process("bans", context);

        context.setVariable("feedbacks", List.of(feedback));
        engine.process("feedback-list", context);

        context.setVariable("status", 403);
        engine.process("error", context);
        context.setVariable("status", 500);
        engine.process("error", context);
        context.setVariable("status", null);
        engine.process("error", context);
    }

    @Test
    void rendersQuestPages() {
        TemplateEngine engine = engine();
        WebContext context = webContext();
        baseVars(context);

        Quest quest = new Quest();
        quest.setId(3L);
        quest.setTitle("Тайны старого парка");
        quest.setDescription("Гуляем по парку и ищем таблички");
        quest.setActive(true);

        QuestCheckpoint checkpoint = new QuestCheckpoint();
        checkpoint.setId(7L);
        checkpoint.setQuestId(3L);
        checkpoint.setTitle("Беседка у пруда");
        checkpoint.setQuestion("Что написано на табличке?");
        checkpoint.setAnswer("Урмары");
        checkpoint.setLat(55.751244);
        checkpoint.setLon(37.618423);
        checkpoint.setRadiusM(50);
        checkpoint.setMediaType("image");
        checkpoint.setMediaFile("123_hint.jpg");

        QuestProgress progress = new QuestProgress(true, false, Set.of(), 0, 1);

        context.setVariable("quests", List.of(quest));
        context.setVariable("checkpointCounts", Map.of(3L, 1L));
        context.setVariable("progress", Map.of(3L, progress));
        context.setVariable("superAdmin", true);
        context.setVariable("quest", quest);
        context.setVariable("checkpoints", List.of(checkpoint));
        context.setVariable("mediaDirs", Map.of(7L, "quest-7"));
        context.setVariable("defaultRadius", 50);
        context.setVariable("maxRadius", 5000);
        context.setVariable("participants", List.of());
        context.setVariable("completed", List.of());
        context.setVariable("totalStarted", 0);
        context.setVariable("checkpointCount", 1L);

        String list = engine.process("quests", context);
        assertThat(list)
                .contains("Тайны старого парка")
                .contains("/app/quests/3/edit")
                .contains("Результаты");

        // На странице квеста в модели лежит прогресс одного участника, а не карта по квестам.
        context.setVariable("progress", progress);
        String page = engine.process("quest", context);
        assertThat(page)
                .contains("Что написано на табличке?")
                .contains("/app/quests/3/answer")
                .contains("quest-7");

        engine.process("quest-edit", context);
        engine.process("quest-results", context);
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
        when(trip.getLogId()).thenReturn(9L);
        when(trip.getEditedAt()).thenReturn(LocalDateTime.of(2026, 10, 15, 15, 0));
        when(trip.getDeleted()).thenReturn(false);

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
        when(message.getMessageId()).thenReturn(10);
        when(message.getEditedAt()).thenReturn(LocalDateTime.of(2026, 10, 15, 15, 30));
        when(message.getDeleted()).thenReturn(true);

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

        LogEdit tripEdit = new LogEdit();
        tripEdit.setLogId(9L);
        tripEdit.setText("Старый текст поездки");
        tripEdit.setEditedAt(LocalDateTime.of(2026, 10, 15, 15, 0));

        LogEdit chatEdit = new LogEdit();
        chatEdit.setLogId(1L);
        chatEdit.setText("Старый привет");
        chatEdit.setEditedAt(LocalDateTime.of(2026, 10, 15, 15, 30));

        context.setVariable("edits", Map.of(9L, List.of(tripEdit), 1L, List.of(chatEdit)));
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
        assertThat(tripsHtml)
                .contains("15 октября 2026, 14:30")
                // правка сообщения: пометка и кнопка с предыдущей версией
                .contains("✏️ изменено")
                .contains("История изменений (1)")
                .contains("Старый текст поездки");

        engine.process("chats", context);
        engine.process("driver-reviews", context);

        String chatHtml = engine.process("chat", context);
        // удалённое сообщение и история правок в истории чата
        assertThat(chatHtml)
                .contains("🗑 удалено")
                .contains("is-deleted")
                .contains("История изменений (1)")
                .contains("Старый привет");

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
