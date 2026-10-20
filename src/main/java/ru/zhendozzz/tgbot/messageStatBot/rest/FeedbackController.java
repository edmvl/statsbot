package ru.zhendozzz.tgbot.messageStatBot.rest;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.dto.ApiResponse;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMemberViewDto;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.FeedbackService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUserService currentUserService;

    public FeedbackController(FeedbackService feedbackService, CurrentUserService currentUserService) {
        this.feedbackService = feedbackService;
        this.currentUserService = currentUserService;
    }

    /** Поиск пользователя, на которого отправляется жалоба. */
    @GetMapping("/users")
    public ResponseEntity<?> findUsers(@RequestParam(value = "q", required = false) String query,
                                       HttpServletRequest request) {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(List.of(Map.of("message", "Откройте приложение через Telegram")));
        }
        List<ChatMemberViewDto> users = feedbackService.findComplainableUsers(query);
        return ResponseEntity.ok(users.stream()
                .map(user -> Map.of(
                        "id", user.getUserId(),
                        "name", displayName(user),
                        "userName", user.getUserName() == null ? "" : user.getUserName()))
                .toList());
    }

    private String displayName(ChatMemberViewDto user) {
        String name = user.getName() == null ? "" : user.getName().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return user.getUserName() != null && !user.getUserName().isEmpty()
                ? "@" + user.getUserName()
                : user.getUserId();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse> submitFeedback(
            @RequestPart("complaint") String complaint,
            @RequestPart(value = "targetUserId", required = false) String targetUserId,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {

        // Автора обращения берём только из подписанного Telegram initData.
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(false, "Откройте приложение через Telegram", null));
        }

        try {
            String feedbackId = feedbackService.processFeedback(complaint, userId, targetUserId, files);
            return ResponseEntity.ok(
                    new ApiResponse(true, "Обращение успешно отправлено", feedbackId)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Ошибка при обработке запроса: " + e.getMessage(), null));
        }
    }

}
