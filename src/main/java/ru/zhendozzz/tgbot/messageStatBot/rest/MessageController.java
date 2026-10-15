package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/")
public class MessageController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final MessageExecutor messageExecutor;
    private final CurrentUserService currentUserService;

    public MessageController(
            MessageExecutor messageExecutor,
            CurrentUserService currentUserService
    ) {
        this.messageExecutor = messageExecutor;
        this.currentUserService = currentUserService;
    }

    /**
     * Отправка произвольного сообщения ботом. Раньше был открытым релеем,
     * теперь доступен только супер-админу (по подписанному Telegram initData).
     */
    @PostMapping("/send")
    public ResponseEntity<?> parse(@RequestBody SendMessage message, HttpServletRequest request) {
        if (!currentUserService.isSuperAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied"));
        }
        return ResponseEntity.ok(messageExecutor.sendMessage(message));
    }

    @GetMapping("/images")
    public ResponseEntity<Resource> getImage(@RequestParam String id, @RequestParam String name) throws IOException {
        Path root = Paths.get(uploadDir).normalize();
        Path filePath = root.resolve(id + "/" + name).normalize();
        if (!filePath.startsWith(root)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        String contentType = Files.probeContentType(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                .body(resource);
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String userId) {
        Path root = Paths.get(uploadDir).normalize();
        Path filePath = root.resolve("avatars/" + userId + ".jpg").normalize();
        if (!filePath.startsWith(root) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(filePath.toFile()));
    }
}
