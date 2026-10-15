package ru.zhendozzz.tgbot.messageStatBot.rest;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.dto.ApiResponse;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.FeedbackService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUserService currentUserService;

    public FeedbackController(FeedbackService feedbackService, CurrentUserService currentUserService) {
        this.feedbackService = feedbackService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse> submitFeedback(
            @RequestPart("complaint") String complaint,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {

        // Автора обращения берём только из подписанного Telegram initData.
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(false, "Откройте приложение через Telegram", null));
        }

        try {
            String feedbackId = feedbackService.processFeedback(complaint, userId, files);
            return ResponseEntity.ok(
                    new ApiResponse(true, "Обращение успешно отправлено", feedbackId)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Ошибка при обработке запроса: " + e.getMessage(), null));
        }
    }

}
