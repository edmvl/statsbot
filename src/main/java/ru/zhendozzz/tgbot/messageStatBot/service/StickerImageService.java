package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Рисует текст сообщения в PNG-стикер для команды {@code .стикер}.
 *
 * <p>Требования Telegram к статическому стикеру: одна из сторон ровно 512 px,
 * вторая не больше 512 px, файл до 512 КБ. Поэтому ширина всегда 512, а высота
 * минимальная под текст — стикер не должен быть громоздким, но шрифт остаётся
 * крупным (26 px), чтобы текст читался в чате.</p>
 */
@Service
@Slf4j
public class StickerImageService {

    /** Ширина стикера: Telegram требует ровно 512 px по одной из сторон. */
    static final int WIDTH = 512;
    /** Максимальная высота — второе ограничение Telegram. */
    static final int MAX_HEIGHT = 512;
    /** Минимальная высота: одна короткая строка не должна превращаться в полоску. */
    static final int MIN_HEIGHT = 96;
    static final int PADDING = 18;
    static final int FONT_SIZE = 26;
    static final int LINE_HEIGHT = 34;
    /** Ограничение Telegram на размер файла статического стикера. */
    static final int MAX_BYTES = 512 * 1024;
    /** Сколько строк влезает в максимальную высоту. */
    static final int MAX_LINES = (MAX_HEIGHT - PADDING * 2) / LINE_HEIGHT;

    private static final String EMPTY_TEXT = "Сообщение без текста";
    private static final String TRUNCATED_MARK = "…";
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color TEXT_COLOR = new Color(0x11, 0x11, 0x11);

    /**
     * Собирает PNG-стикер из текста сообщения.
     *
     * @param text текст исходного сообщения
     * @return байты PNG, готовые к загрузке в Telegram
     */
    public byte[] render(String text) {
        String content = Objects.isNull(text) || text.isBlank() ? EMPTY_TEXT : text.trim();
        Font font = new Font("SansSerif", Font.PLAIN, FONT_SIZE);

        List<String> lines = wrap(content, font, WIDTH - PADDING * 2);
        if (lines.isEmpty()) {
            lines = List.of(EMPTY_TEXT);
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES - 1));
            lines.add(TRUNCATED_MARK);
        }

        int height = Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, PADDING * 2 + lines.size() * LINE_HEIGHT));
        byte[] png = write(draw(lines, font, height));
        if (png.length <= MAX_BYTES) {
            return png;
        }
        // Страховка: больше 512 КБ Telegram не примет, а текст в оттенках серого весит меньше.
        log.warn("PNG стикера получился {} байт, перерисовываю в оттенках серого", png.length);
        return write(grayscale(draw(lines, font, height)));
    }

    private BufferedImage draw(List<String> lines, Font font, int height) {
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, WIDTH, height);

            graphics.setFont(font);
            graphics.setColor(TEXT_COLOR);
            FontMetrics metrics = graphics.getFontMetrics(font);
            int textHeight = lines.size() * LINE_HEIGHT;
            int y = Math.max(PADDING, (height - textHeight) / 2) + metrics.getAscent();
            for (String line : lines) {
                graphics.drawString(line, PADDING, y);
                y += LINE_HEIGHT;
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Переносит текст по словам; слово длиннее строки режется по символам. */
    private List<String> wrap(String text, Font font, int maxWidth) {
        List<String> lines = new ArrayList<>();
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = probe.createGraphics();
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        try {
            for (String paragraph : text.split("\\R")) {
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.trim().split("\\s+")) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    String candidate = current.length() == 0 ? word : current + " " + word;
                    if (metrics.stringWidth(candidate) <= maxWidth) {
                        current.setLength(0);
                        current.append(candidate);
                        continue;
                    }
                    if (current.length() > 0) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(word);
                    while (metrics.stringWidth(current.toString()) > maxWidth && current.length() > 1) {
                        int cut = current.length() - 1;
                        while (cut > 1 && metrics.stringWidth(current.substring(0, cut)) > maxWidth) {
                            cut--;
                        }
                        lines.add(current.substring(0, cut));
                        current.delete(0, cut);
                    }
                }
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                if (lines.size() > MAX_LINES) {
                    // Дальше всё равно обрежем — лишние строки не считаем.
                    break;
                }
            }
        } finally {
            graphics.dispose();
        }
        return lines;
    }

    private BufferedImage grayscale(BufferedImage source) {
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = gray.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return gray;
    }

    private byte[] write(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось собрать PNG стикера", e);
        }
    }
}
