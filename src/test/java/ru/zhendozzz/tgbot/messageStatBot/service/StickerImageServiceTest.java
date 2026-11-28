package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class StickerImageServiceTest {

    private final StickerImageService service = new StickerImageService();

    @Test
    void keepsTelegramWidthAndMinimalHeightForShortText() throws IOException {
        byte[] png = service.render("Привет, чат!");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(image.getHeight()).isEqualTo(StickerImageService.MIN_HEIGHT);
        assertThat(png.length).isLessThan(StickerImageService.MAX_BYTES);
    }

    @Test
    void growsHeightForLongerTextButStaysWithinTelegramLimit() throws IOException {
        byte[] png = service.render("Это довольно длинное сообщение, которое должно занять "
                + "несколько строк и увеличить высоту стикера, но не выйти за лимит Telegram.");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(image.getHeight()).isGreaterThan(StickerImageService.MIN_HEIGHT);
        assertThat(image.getHeight()).isLessThanOrEqualTo(StickerImageService.MAX_HEIGHT);
    }

    @Test
    void truncatesHugeTextWithEllipsis() throws IOException {
        String huge = ("Длинная строка текста для проверки обрезки стикера. ").repeat(40);

        byte[] png = service.render(huge);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getHeight()).isEqualTo(StickerImageService.MAX_HEIGHT);
        assertThat(png.length).isLessThan(StickerImageService.MAX_BYTES);
    }

    @Test
    void rendersFallbackForEmptyText() throws IOException {
        byte[] png = service.render("   ");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(png.length).isGreaterThan(0);
    }
}
