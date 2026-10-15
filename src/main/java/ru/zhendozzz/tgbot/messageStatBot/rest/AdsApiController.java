package ru.zhendozzz.tgbot.messageStatBot.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.entity.Ads;
import ru.zhendozzz.tgbot.messageStatBot.service.AdsService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdsApiController {

    private final AdsService adsService;

    @GetMapping
    public ResponseEntity<List<Ads>> getAllAds() {
        return ResponseEntity.ok(adsService.getAllAds());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAd(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adsService.getAdById(id));
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    /** Одобрение предложения пользователя: после оплаты звёздами оно станет обычной рекламой. */
    @PostMapping("/proposals/{id}/approve")
    public ResponseEntity<?> approveProposal(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adsService.approveProposal(id));
        } catch (IllegalStateException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    @PostMapping("/proposals/{id}/reject")
    public ResponseEntity<?> rejectProposal(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adsService.rejectProposal(id));
        } catch (IllegalStateException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    @PostMapping
    public ResponseEntity<?> createAd(@RequestBody Ads ad) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(adsService.saveAd(ad));
        } catch (Exception e) {
            return badRequest("Ошибка при создании поста: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAd(@PathVariable Long id, @RequestBody Ads ad) {
        try {
            return ResponseEntity.ok(adsService.updateAd(id, ad));
        } catch (IllegalStateException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            return notFound();
        } catch (Exception e) {
            return badRequest("Ошибка при обновлении: " + e.getMessage());
        }
    }

    @PostMapping(path = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFiles(
            @PathVariable Long id,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        try {
            adsService.uploadFiles(id, files);
            return ResponseEntity.ok(adsService.getAdById(id));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        try {
            adsService.deleteFile(id, fileId);
            return ResponseEntity.ok(adsService.getAdById(id));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> getFile(@PathVariable Long fileId) {
        File file = adsService.loadFile(fileId);
        if (Objects.isNull(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = null;
        try {
            contentType = Files.probeContentType(file.toPath());
        } catch (IOException ignored) {
            // тип не определился — отдадим как бинарный поток
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        Objects.nonNull(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(new FileSystemResource(file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAd(@PathVariable Long id) {
        try {
            adsService.deleteAd(id);
            return ResponseEntity.ok(Map.of("message", "Пост удален"));
        } catch (RuntimeException e) {
            return notFound();
        }
    }

    private ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Пост не найден"));
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
