package ru.zhendozzz.tgbot.messageStatBot.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.entity.Ads;
import ru.zhendozzz.tgbot.messageStatBot.service.AdsService;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Пользовательские предложения рекламы. Доступны без прав супер-админа,
 * поэтому живут вне защищённого префикса {@code /api/ads}.
 *
 * <p>Пользователь везде определяется по подписанному Telegram {@code initData} —
 * {@code userId} из запроса не принимается.</p>
 */
@RestController
@RequestMapping("/api/ad-proposals")
@RequiredArgsConstructor
public class AdProposalController {

    private final AdsService adsService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<?> myProposals(HttpServletRequest request) {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return notAuthorized();
        }
        return ResponseEntity.ok(adsService.getUserProposals(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProposal(@PathVariable Long id, HttpServletRequest request) {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return notAuthorized();
        }
        try {
            return ResponseEntity.ok(adsService.getUserProposal(id, userId));
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createProposal(
            @RequestParam("chatId") String chatId,
            @RequestParam("message") String message,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return notAuthorized();
        }
        try {
            Ads proposal = adsService.createProposal(userId, chatId, message, days, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(proposal);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Не удалось отправить предложение: " + e.getMessage()));
        }
    }

    /** Ссылка на оплату звёздами для одобренного предложения. */
    @PostMapping("/{id}/invoice")
    public ResponseEntity<?> createInvoice(@PathVariable Long id, HttpServletRequest request) {
        String userId = currentUserService.idAsString(request);
        if (userId == null) {
            return notAuthorized();
        }
        try {
            String link = adsService.createInvoiceLink(id, userId);
            if (link == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Не удалось создать счёт на оплату"));
            }
            return ResponseEntity.ok(Map.of("link", link));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> notAuthorized() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Откройте приложение через Telegram"));
    }

    private ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Предложение не найдено"));
    }
}
