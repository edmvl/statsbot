package ru.zhendozzz.tgbot.messageStatBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.FileDto;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
public class FileLoaderService implements NeedProcessMessage {

    @Value("${file.upload-dir}")
    private String uploadDir;
    @Value("${telegram.bot.token}")
    private String token;

    public FileLoaderService() {
    }

    @SneakyThrows
    public void downloadFile(FileDto fileDto) {
        String jsonString = getHTMLPage("https://api.telegram.org/bot" + token + "/getFile?file_id=" + fileDto.getFileId());
        if (Objects.nonNull(jsonString)) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonString);
            String file_path = rootNode.get("result").get("file_path").asText();
            objectMapper.readValue(jsonString, HashMap.class);
            String url = "https://api.telegram.org/file/bot" + token + "/" + file_path;
            File destination = new File(uploadDir + fileDto.getFileNameForSave());
            FileUtils.copyURLToFile(
                    new URL(url),
                    destination,
                    1000,
                    100000);
        }
    }

    public static String getHTMLPage(String url) {
        try {
            Connection.Response execute = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("https://api.telegram.org")
                    .followRedirects(true)
                    .cookie("stel_ln", "it")
                    .ignoreContentType(true)
                    .execute();
            return execute.parse().text();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void processMessage(MessageDTO messageDTO, Update update) {
        if (Objects.nonNull(messageDTO.getFile())) {
            //downloadFile(messageDTO.getFile());
        }
    }

    public void processFiles(List<MultipartFile> files, String feedbackId) {
        try {
            Path uploadPath = Paths.get(uploadDir, feedbackId);
            Files.createDirectories(uploadPath);
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                    Path filePath = uploadPath.resolve(fileName);
                    Files.copy(file.getInputStream(), filePath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении файлов", e);
        }
    }

    /**
     * Рисует текст сообщения в PNG — используется командой {@code .бан},
     * чтобы в обращении остался скриншот исходного сообщения.
     */
    public String saveMessageScreenshot(Long feedbackId, String text) {
        String content = Objects.isNull(text) || text.trim().isEmpty()
                ? "Сообщение без текста"
                : text.trim();
        Path uploadPath = Paths.get(uploadDir, String.valueOf(feedbackId));
        try {
            Files.createDirectories(uploadPath);

            int width = 720;
            int padding = 24;
            int headerHeight = 42;
            int lineHeight = 26;
            Font bodyFont = new Font("SansSerif", Font.PLAIN, 18);
            Font headerFont = new Font("SansSerif", Font.BOLD, 14);

            BufferedImage probe = new BufferedImage(width, 10, BufferedImage.TYPE_INT_RGB);
            Graphics2D probeGraphics = probe.createGraphics();
            probeGraphics.setFont(bodyFont);
            List<String> lines = wrapText(content, probeGraphics.getFontMetrics(bodyFont), width - padding * 2);
            probeGraphics.dispose();

            int height = padding * 2 + headerHeight + Math.max(1, lines.size()) * lineHeight;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);

            graphics.setFont(headerFont);
            graphics.setColor(new Color(0x70, 0x75, 0x79));
            graphics.drawString("Сообщение в чате", padding, padding + 14);

            graphics.setFont(bodyFont);
            graphics.setColor(new Color(0x11, 0x11, 0x11));
            FontMetrics metrics = graphics.getFontMetrics(bodyFont);
            int y = padding + headerHeight + metrics.getAscent();
            for (String line : lines) {
                graphics.drawString(line, padding, y);
                y += lineHeight;
            }
            graphics.dispose();

            String fileName = "message_" + System.currentTimeMillis() + ".png";
            ImageIO.write(image, "png", uploadPath.resolve(fileName).toFile());
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить скриншот сообщения", e);
        }
    }

    private List<String> wrapText(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (metrics.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    while (metrics.stringWidth(word) > maxWidth && word.length() > 1) {
                        int cut = 1;
                        while (cut < word.length() && metrics.stringWidth(word.substring(0, cut + 1)) <= maxWidth) {
                            cut++;
                        }
                        lines.add(word.substring(0, cut));
                        word = word.substring(cut);
                    }
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    public List<String> getFiles(Long feedbackId) {
        return getFiles(String.valueOf(feedbackId));
    }

    /**
     * Сохраняет один файл в каталог с ключом (например, {@code quest-12}) и возвращает
     * имя сохранённого файла — его храним в БД и отдаём через {@code /images}.
     */
    public String saveSingleFile(MultipartFile file, String key) {
        try {
            Path uploadPath = Paths.get(uploadDir, key);
            Files.createDirectories(uploadPath);
            String fileName = System.currentTimeMillis() + "_" + safeFileName(file.getOriginalFilename());
            Files.copy(file.getInputStream(), uploadPath.resolve(fileName));
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении файла", e);
        }
    }

    /** Удаляет файлы каталога с ключом (при замене или удалении вложения). */
    public void clearFiles(String key) {
        Path uploadPath = Paths.get(uploadDir, key);
        try (var walk = Files.walk(uploadPath)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // файл занят — не критично, останется мусором
                }
            });
        } catch (IOException ignored) {
            // каталога нет — удалять нечего
        }
    }

    /** Имя без каталогов и опасных символов. */
    private String safeFileName(String originalName) {
        String name = Objects.toString(originalName, "file");
        name = name.replace('\\', '_').replace('/', '_');
        name = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return name.isEmpty() ? "file" : name;
    }

    /** Файлы из каталога с произвольным ключом (для вложений к комментариям). */
    public List<String> getFiles(String key) {
        Path uploadPath = Paths.get(uploadDir, key);
        List<String> files;
        try (var walk = Files.walk(uploadPath)) {
            files = walk
                    .filter(Files::isRegularFile)
                    .map(path -> uploadPath.relativize(path).toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return files;
    }
}
