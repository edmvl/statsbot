package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileLoaderServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesMessageTextAsPngScreenshot() {
        FileLoaderService service = new FileLoaderService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        String fileName = service.saveMessageScreenshot(15L, "Текст сообщения для обращения");

        Path file = tempDir.resolve("15").resolve(fileName);
        assertThat(file).exists();
        assertThat(file.getFileName().toString()).endsWith(".png");
        assertThat(Files.isRegularFile(file)).isTrue();
    }
}
