package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.dto.StickerMessage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class StickerImageServiceTest {

    private final StickerImageService service = new StickerImageService();

    @Test
    void keepsTelegramWidthAndMinimalHeightForShortText() throws IOException {
        byte[] png = service.render(message("Привет!"));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(image.getHeight()).isEqualTo(StickerImageService.MIN_HEIGHT);
        assertThat(png.length).isLessThan(StickerImageService.MAX_BYTES);
    }

    @Test
    void growsHeightForLongerTextButStaysWithinTelegramLimit() throws IOException {
        byte[] png = service.render(message("Это довольно длинное сообщение, которое должно занять "
                + "несколько строк и увеличить высоту стикера, но не выйти за лимит Telegram."));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(image.getHeight()).isGreaterThan(StickerImageService.MIN_HEIGHT);
        assertThat(image.getHeight()).isLessThanOrEqualTo(StickerImageService.MAX_HEIGHT);
    }

    @Test
    void truncatesHugeTextAndStaysWithinTelegramLimit() throws IOException {
        String huge = ("Длинная строка текста для проверки обрезки стикера. ").repeat(40);

        byte[] png = service.render(message(huge));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getHeight()).isGreaterThan(400);
        assertThat(image.getHeight()).isLessThanOrEqualTo(StickerImageService.MAX_HEIGHT);
        assertThat(png.length).isLessThan(StickerImageService.MAX_BYTES);
    }

    @Test
    void rendersFallbackForEmptyText() throws IOException {
        byte[] png = service.render(message("   "));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(png.length).isGreaterThan(0);
    }

    @Test
    void survivesMessageWithoutAuthor() throws IOException {
        byte[] png = service.render(StickerMessage.builder().text("без автора").build());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(StickerImageService.WIDTH);
        assertThat(png.length).isGreaterThan(0);
    }

    @Test
    void keepsTransparentBackground() throws IOException {
        byte[] png = service.render(message("прозрачность"));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image.getColorModel().hasAlpha()).isTrue();
        int alpha = (image.getRGB(StickerImageService.WIDTH - 1, image.getHeight() - 1) >> 24) & 0xff;
        assertThat(alpha).isZero();
    }

    private StickerMessage message(String text) {
        return StickerMessage.builder()
                .authorName("Иван Петров")
                .status("админ")
                .time("19:00")
                .text(text)
                .build();
    }
}
