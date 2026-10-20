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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public List<String> getFiles(Long feedbackId) {
        return getFiles(String.valueOf(feedbackId));
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
