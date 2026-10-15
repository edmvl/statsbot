package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatViewDto;
import ru.zhendozzz.tgbot.messageStatBot.config.AdminAuthGuard;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBackChat;
import ru.zhendozzz.tgbot.messageStatBot.entity.TgUser;
import ru.zhendozzz.tgbot.messageStatBot.repo.RatingRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;
import ru.zhendozzz.tgbot.messageStatBot.service.FeedbackService;
import ru.zhendozzz.tgbot.messageStatBot.service.AdsService;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.FileLoaderService;
import ru.zhendozzz.tgbot.messageStatBot.service.InterestedService;
import ru.zhendozzz.tgbot.messageStatBot.service.LogService;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import ru.zhendozzz.tgbot.messageStatBot.service.TripService;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.AdStatus;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/app")
public class MiniAppController {

    private final TripService tripService;
    private final FeedbackService feedbackService;
    private final FileLoaderService fileLoaderService;
    private final LogService logService;
    private final InterestedService interestedService;
    private final SettingsService settingsService;
    private final RatingRepo ratingRepo;
    private final UserRepo userRepo;
    private final CurrentUserService currentUserService;
    private final AdminAuthGuard adminAuthGuard;

    public MiniAppController(
            TripService tripService,
            FeedbackService feedbackService, FileLoaderService fileLoaderService,
            LogService logService, InterestedService interestedService,
            SettingsService settingsService, RatingRepo ratingRepo, UserRepo userRepo,
            CurrentUserService currentUserService, AdminAuthGuard adminAuthGuard
    ) {
        this.tripService = tripService;
        this.feedbackService = feedbackService;
        this.fileLoaderService = fileLoaderService;
        this.logService = logService;
        this.interestedService = interestedService;
        this.settingsService = settingsService;
        this.ratingRepo = ratingRepo;
        this.userRepo = userRepo;
        this.currentUserService = currentUserService;
        this.adminAuthGuard = adminAuthGuard;
    }

    @GetMapping("")
    public String index() {
        return "index";
    }

    @GetMapping("/chat/{chatId}")
    public String tripView(
            @PathVariable String chatId,
            @QueryParam("page") String page,
            @QueryParam("count") String count,
            Model model
    ) {
        int currentPage = Integer.parseInt(Objects.nonNull(page) ? page : "0");
        int cnt = Integer.parseInt(Objects.nonNull(count) ? count : "30");
        List<ChatMessageDto> messages = logService.findByChatId(chatId, currentPage, cnt);
        model.addAttribute("messages", messages);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("count", cnt);
        model.addAttribute("chatId", chatId);
        model.addAttribute("chatName", logService.findChatName(chatId));
        model.addAttribute("nextPage", currentPage + 1);
        model.addAttribute("prevPage", Math.max(currentPage - 1, 0));
        return "chat";
    }

    /**
     * Страница всех отзывов о водителе: видны автор отзыва и его данные.
     */
    @GetMapping("/reviews/{userId}")
    public String driverReviews(@PathVariable("userId") String userId, Model model) {
        TgUser driver = userRepo.findById(userId).orElse(null);
        model.addAttribute("driver", driver);
        model.addAttribute("driverId", userId);
        model.addAttribute("reviews", ratingRepo.findDriverReviews(userId));
        return "driver-reviews";
    }

    /**
     * Страница подписок ({@code interested}): кто на кого подписан, сгруппировано
     * по подписавшемуся.
     */
    @GetMapping("/subscriptions")
    public String subscriptions(Model model) {
        model.addAttribute("groups", interestedService.findAllGroupedBySubscriber());
        return "subscriptions";
    }

    /**
     * Личные подписки пользователя: на кого подписан он сам, с возможностью отписаться.
     * Пользователь берётся из подписанного Telegram initData.
     */
    @GetMapping("/my-subscriptions")
    public String mySubscriptions(HttpServletRequest request, Model model) {
        String userId = currentUserService.idAsString(request);
        model.addAttribute("currentUserId", userId);
        model.addAttribute("subscriptions", interestedService.findBySubscriber(userId));
        return "my-subscriptions";
    }

    /** Отписка от автора публикаций. */
    @PostMapping("/my-subscriptions")
    public String unsubscribe(
            @RequestParam("interestId") String interestId,
            @RequestParam(value = "chatId", required = false) String chatId,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse
    ) throws IOException {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Откройте приложение через Telegram");
            return null;
        }
        interestedService.unsubscribe(userId, chatId, interestId);
        httpServletResponse.sendRedirect("/app/my-subscriptions");
        return "my-subscriptions";
    }

    @GetMapping("/chats")
    public String chatsView(Model model) {
        List<ChatViewDto> chats = logService.getAllChats();
        model.addAttribute("chats", chats);
        return "chats";
    }

    @GetMapping("/trips")
    public String trips(
            @QueryParam("page") String page,
            @QueryParam("count") String count,
            @QueryParam("dateFrom") String dateFrom,
            @QueryParam("dateTo") String dateTo,
            @QueryParam("author") String author,
            @QueryParam("text") String text,
            Model model
    ) {
        int currentPage = Integer.parseInt(Objects.nonNull(page) ? page : "0");
        int cnt = Integer.parseInt(Objects.nonNull(count) ? count : "30");
        List<TripViewDto> tripList = tripService.getTripList(
                currentPage * cnt,
                cnt,
                parseDateStart(dateFrom),
                parseDateEnd(dateTo),
                author,
                text
        );
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("count", cnt);
        model.addAttribute("nextPage", currentPage + 1);
        model.addAttribute("prevPage", Math.max(currentPage - 1, 0));
        model.addAttribute("tripList", tripList);
        model.addAttribute("dateFrom", defaultValue(dateFrom));
        model.addAttribute("dateTo", defaultValue(dateTo));
        model.addAttribute("author", defaultValue(author));
        model.addAttribute("text", defaultValue(text));
        return "trips";
    }

    private LocalDateTime parseDateStart(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime parseDateEnd(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atTime(23, 59, 59);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String defaultValue(String value) {
        return value == null ? "" : value;
    }

    @PostMapping("/trips")
    public String tripsAdd(
            @RequestParam("userId") String reviewedUserId,
            @RequestParam(value = "rating", required = false) String rating,
            @RequestParam(value = "comment", required = false) String comment,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse) throws IOException {
        // Автора отзыва берём только из подписанного Telegram initData, а не из формы.
        String reviewerId = currentUserService.idAsString(request);
        int stars = parseStars(rating);
        if (reviewerId == null || stars == 0) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Откройте приложение через Telegram");
            return null;
        }
        tripService.addRating(reviewedUserId, reviewerId, String.valueOf(stars), truncate(comment, 255));
        httpServletResponse.sendRedirect("trips");
        return "trips";
    }

    private int parseStars(String rating) {
        if (Objects.isNull(rating)) {
            return 0;
        }
        try {
            int stars = Integer.parseInt(rating.trim());
            return (stars >= 1 && stars <= 5) ? stars : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String truncate(String value, int max) {
        if (Objects.isNull(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * Список id авторов публикаций, на которых подписан пользователь в канале поездок.
     * Используется формой просмотра поездок для начального состояния переключателей.
     */
    @GetMapping("/interested")
    @ResponseBody
    public List<String> getInterested(HttpServletRequest request) {
        String userId = currentUserService.idAsString(request);
        String chatId = settingsService.findTripChannelId();
        if (userId == null || chatId == null) {
            return Collections.emptyList();
        }
        return interestedService.findSubscribedInterestIds(userId, chatId);
    }

    /**
     * Подписка/отписка от автора публикации. Пользователь берётся из подписанного
     * Telegram initData, {@code interestId} — id автора публикации.
     */
    @PostMapping("/interested")
    @ResponseBody
    public Map<String, Object> updateInterested(
            @RequestParam("interestId") String interestId,
            @RequestParam("subscribed") boolean subscribed,
            HttpServletRequest request
    ) {
        Map<String, Object> result = new HashMap<>();
        String userId = currentUserService.idAsString(request);
        String chatId = settingsService.findTripChannelId();
        if (userId == null) {
            result.put("success", false);
            result.put("message", "Откройте приложение через Telegram");
            return result;
        }
        if (chatId == null || interestId.trim().isEmpty()) {
            result.put("success", false);
            return result;
        }
        if (subscribed) {
            interestedService.subscribe(userId, chatId, interestId.trim());
        } else {
            interestedService.unsubscribe(userId, chatId, interestId.trim());
        }
        result.put("success", true);
        result.put("subscribed", subscribed);
        return result;
    }

    @GetMapping("/feedback")
    public String feedback(Model model) {
        return "feedback";
    }

    @GetMapping("/ads")
    public String ads(Model model) {
        model.addAttribute("chats", chatsWithAdsEnabled());
        model.addAttribute("starsPerDay", AdStatus.STARS_PER_DAY);
        return "ads";
    }

    /**
     * Страница предложения рекламы: пользователь заполняет форму, а после
     * одобрения супер-админом оплачивает показ звёздами. Реклама доступна
     * только для чатов с включённой настройкой «Показывать рекламу».
     */
    @GetMapping("/ad-proposal")
    public String adProposal(Model model) {
        model.addAttribute("chats", chatsWithAdsEnabled());
        model.addAttribute("starsPerDay", AdStatus.STARS_PER_DAY);
        model.addAttribute("maxDays", AdStatus.MAX_DAYS);
        model.addAttribute("maxFiles", AdsService.MAX_FILES);
        return "ad-proposal";
    }

    /** Чаты, в которых включена настройка «Показывать рекламу». */
    private List<ChatViewDto> chatsWithAdsEnabled() {
        return logService.getAllChats().stream()
                .filter(chat -> settingsService.isEnabled(chat.getChatId(), ChatSettingConstants.ENABLE_ADS))
                .toList();
    }

    @GetMapping("/feedback-chat/{id}")
    public String feedbackChat(
            Model model,
            @PathVariable("id") Long feedbackId,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse
    ) throws IOException {
        // Переписка по обращениям: супер-админ и админы канала поездок.
        if (!adminAuthGuard.allow(request, httpServletResponse, true)) {
            return null;
        }
        List<FeedBackChat> feedBackChatList = feedbackService.findChatByFeedbackId(feedbackId);
        FeedBack feedback = feedbackService.findFeedbackById(feedbackId);
        model.addAttribute("feedBackChatList", feedBackChatList);
        model.addAttribute("feedback", feedback);
        model.addAttribute("files", fileLoaderService.getFiles(feedbackId));
        return "feedback-chat";
    }

    @PostMapping("/feedback-chat")
    public String feedbackChatReply(
            Model model,
            @RequestParam("id") Long feedbackId,
            @RequestParam("text") String text,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse
    ) throws IOException {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Откройте приложение через Telegram");
            return null;
        }
        if (!adminAuthGuard.allow(request, httpServletResponse, true)) {
            return null;
        }
        feedbackService.addChatByFeedbackId(feedbackId, userId, text);
        List<FeedBackChat> feedBackChatList = feedbackService.findChatByFeedbackId(feedbackId);
        FeedBack feedback = feedbackService.findFeedbackById(feedbackId);
        model.addAttribute("feedBackChatList", feedBackChatList);
        model.addAttribute("feedback", feedback);
        model.addAttribute("files", fileLoaderService.getFiles(feedbackId));
        httpServletResponse.sendRedirect("/app/feedback-chat/" + feedbackId);
        return "feedback-chat";
    }
}
