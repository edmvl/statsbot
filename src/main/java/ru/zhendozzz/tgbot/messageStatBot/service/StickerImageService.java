package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.dto.StickerMessage;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Рисует сообщение в PNG-стикер для команды {@code .стикер}.
 *
 * <p>Картинка повторяет вид сообщения в группе Telegram: круглый аватар, пузырь
 * с именем автора и его статусом («владелец»/«админ»/«канал»), текстом и временем.
 * Фон прозрачный, поэтому стикер нормально смотрится и в светлой, и в тёмной теме.</p>
 *
 * <p>Требования Telegram к статическому стикеру: одна из сторон ровно 512 px,
 * вторая не больше 512 px, файл до 512 КБ. Ширина всегда 512, высота — по тексту.</p>
 */
@Service
@Slf4j
public class StickerImageService {

    /** Ширина стикера: Telegram требует ровно 512 px по одной из сторон. */
    static final int WIDTH = 512;
    /** Максимальная высота — второе ограничение Telegram. */
    static final int MAX_HEIGHT = 512;
    /** Минимальная высота: аватар и пузырь в одну строку. */
    static final int MIN_HEIGHT = 120;
    static final int MARGIN = 14;
    static final int AVATAR_SIZE = 44;
    static final int BUBBLE_X = MARGIN + AVATAR_SIZE + 10;
    static final int BUBBLE_WIDTH = WIDTH - BUBBLE_X - MARGIN;
    static final int BUBBLE_RADIUS = 14;
    static final int PADDING = 12;
    /** Высота строки с именем автора внутри пузыря. */
    static final int NAME_ROW = 26;
    static final int TEXT_SIZE = 22;
    static final int TEXT_LINE_HEIGHT = 30;
    /** Место под время в правом нижнем углу пузыря. */
    static final int TIME_RESERVE = 22;
    /** Ограничение Telegram на размер файла статического стикера. */
    static final int MAX_BYTES = 512 * 1024;
    /** Сколько строк текста влезает в максимальную высоту. */
    static final int MAX_LINES = (MAX_HEIGHT - MARGIN * 2 - PADDING - NAME_ROW - TIME_RESERVE)
            / TEXT_LINE_HEIGHT;

    private static final String EMPTY_TEXT = "Сообщение без текста";
    private static final String TRUNCATED_MARK = "…";

    private static final Color NAME_COLOR = new Color(0x33, 0x90, 0xEC);
    private static final Color STATUS_COLOR = new Color(0x7A, 0x8A, 0x99);
    private static final Color TEXT_COLOR = new Color(0x11, 0x11, 0x11);
    private static final Color BUBBLE_COLOR = Color.WHITE;
    private static final Color BUBBLE_BORDER = new Color(0xE3, 0xE8, 0xEC);
    /** Палитра аватарок-заглушек, как в приложении. */
    private static final Color[] AVATAR_COLORS = {
            new Color(0xE1, 0x70, 0x55), new Color(0xF2, 0x9E, 0x3B), new Color(0x8E, 0x7C, 0xC3),
            new Color(0x5C, 0xA8, 0x5D), new Color(0x3E, 0x9A, 0xD8), new Color(0xC9, 0x5B, 0x86),
    };

    private final Font textFont = new Font("SansSerif", Font.PLAIN, TEXT_SIZE);
    private final Font nameFont = new Font("SansSerif", Font.BOLD, 20);
    private final Font statusFont = new Font("SansSerif", Font.PLAIN, 14);
    private final Font timeFont = new Font("SansSerif", Font.PLAIN, 15);
    private final Font initialsFont = new Font("SansSerif", Font.BOLD, 18);

    /**
     * Собирает PNG-стикер из сообщения.
     *
     * @param message автор, статус, время и текст
     * @return байты PNG, готовые к загрузке в Telegram
     */
    public byte[] render(StickerMessage message) {
        StickerMessage source = Objects.isNull(message) ? StickerMessage.builder().build() : message;
        String content = Objects.isNull(source.getText()) || source.getText().isBlank()
                ? EMPTY_TEXT
                : source.getText().trim();

        List<String> lines = wrap(content, textFont, BUBBLE_WIDTH - PADDING * 2);
        if (lines.isEmpty()) {
            lines = List.of(EMPTY_TEXT);
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES - 1));
            lines.add(TRUNCATED_MARK);
        }

        int bubbleTop = MARGIN;
        int bubbleHeight = PADDING + NAME_ROW + lines.size() * TEXT_LINE_HEIGHT + TIME_RESERVE;
        int height = Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, bubbleTop + bubbleHeight + MARGIN));

        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawAvatar(graphics, source);
            drawBubble(graphics, source, lines, bubbleTop, bubbleHeight);
            drawTail(graphics, bubbleTop);
        } finally {
            graphics.dispose();
        }

        byte[] png = write(image);
        if (png.length > MAX_BYTES) {
            log.warn("PNG стикера получился {} байт — больше лимита Telegram в 512 КБ", png.length);
        }
        return png;
    }

    /** Круглый аватар: фото автора, а если его нет — инициалы на цветном круге. */
    private void drawAvatar(Graphics2D graphics, StickerMessage message) {
        Shape clip = new Ellipse2D.Float(MARGIN, MARGIN, AVATAR_SIZE, AVATAR_SIZE);
        Shape oldClip = graphics.getClip();
        graphics.setClip(clip);
        BufferedImage avatar = message.getAvatar();
        if (Objects.nonNull(avatar) && avatar.getWidth() > 0 && avatar.getHeight() > 0) {
            double scale = Math.max(AVATAR_SIZE / (double) avatar.getWidth(),
                    AVATAR_SIZE / (double) avatar.getHeight());
            int width = (int) Math.round(avatar.getWidth() * scale);
            int height = (int) Math.round(avatar.getHeight() * scale);
            graphics.drawImage(avatar, MARGIN + (AVATAR_SIZE - width) / 2, MARGIN + (AVATAR_SIZE - height) / 2,
                    width, height, null);
        } else {
            graphics.setColor(avatarColor(message.getAuthorName()));
            graphics.fillOval(MARGIN, MARGIN, AVATAR_SIZE, AVATAR_SIZE);
            String initials = initials(message.getAuthorName());
            graphics.setFont(initialsFont);
            graphics.setColor(Color.WHITE);
            FontMetrics metrics = graphics.getFontMetrics();
            int x = MARGIN + (AVATAR_SIZE - metrics.stringWidth(initials)) / 2;
            int y = MARGIN + (AVATAR_SIZE + metrics.getAscent() - metrics.getDescent()) / 2;
            graphics.drawString(initials, x, y);
        }
        graphics.setClip(oldClip);
    }

    /** Пузырь: имя со статусом сверху, текст, время в правом нижнем углу. */
    private void drawBubble(Graphics2D graphics, StickerMessage message, List<String> lines,
                            int top, int height) {
        graphics.setColor(BUBBLE_COLOR);
        graphics.fillRoundRect(BUBBLE_X, top, BUBBLE_WIDTH, height, BUBBLE_RADIUS, BUBBLE_RADIUS);
        graphics.setColor(BUBBLE_BORDER);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRoundRect(BUBBLE_X, top, BUBBLE_WIDTH, height, BUBBLE_RADIUS, BUBBLE_RADIUS);

        drawAuthor(graphics, message, top);

        graphics.setFont(textFont);
        graphics.setColor(TEXT_COLOR);
        FontMetrics metrics = graphics.getFontMetrics();
        int y = top + PADDING + NAME_ROW + metrics.getAscent();
        for (String line : lines) {
            graphics.drawString(line, BUBBLE_X + PADDING, y);
            y += TEXT_LINE_HEIGHT;
        }

        String time = Objects.isNull(message.getTime()) ? "" : message.getTime();
        if (!time.isBlank()) {
            graphics.setFont(timeFont);
            graphics.setColor(STATUS_COLOR);
            FontMetrics timeMetrics = graphics.getFontMetrics();
            graphics.drawString(time,
                    BUBBLE_X + BUBBLE_WIDTH - PADDING - timeMetrics.stringWidth(time),
                    top + height - 7);
        }
    }

    /** Имя автора и статус в чате — первая строка внутри пузыря. */
    private void drawAuthor(Graphics2D graphics, StickerMessage message, int top) {
        String name = Objects.isNull(message.getAuthorName()) || message.getAuthorName().isBlank()
                ? "Аноним"
                : message.getAuthorName().trim();
        String status = message.getStatus();

        graphics.setFont(statusFont);
        FontMetrics statusMetrics = graphics.getFontMetrics();
        int statusWidth = Objects.isNull(status) || status.isBlank()
                ? 0
                : statusMetrics.stringWidth(status) + 8;

        graphics.setFont(nameFont);
        FontMetrics nameMetrics = graphics.getFontMetrics();
        int maxNameWidth = BUBBLE_WIDTH - PADDING * 2 - statusWidth;
        if (nameMetrics.stringWidth(name) > maxNameWidth) {
            name = cutToWidth(name, nameMetrics, maxNameWidth);
        }
        int baseline = top + PADDING + 16;
        graphics.setColor(NAME_COLOR);
        graphics.drawString(name, BUBBLE_X + PADDING, baseline);

        if (statusWidth > 0) {
            graphics.setFont(statusFont);
            graphics.setColor(STATUS_COLOR);
            graphics.drawString(status, BUBBLE_X + PADDING + nameMetrics.stringWidth(name) + 8, baseline);
        }
    }

    /** Хвостик пузыря, как у входящего сообщения в приложении. */
    private void drawTail(Graphics2D graphics, int top) {
        Path2D tail = new Path2D.Float();
        tail.moveTo(BUBBLE_X + 14, top + 1);
        tail.curveTo(BUBBLE_X + 4, top + 2, BUBBLE_X - 2, top + 5, BUBBLE_X - 6, top + 12);
        tail.lineTo(BUBBLE_X + 14, top + 16);
        tail.closePath();
        graphics.setColor(BUBBLE_COLOR);
        graphics.fill(tail);
        graphics.setColor(BUBBLE_BORDER);
        graphics.setStroke(new BasicStroke(1f));
        graphics.draw(tail);
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

    private String cutToWidth(String text, FontMetrics metrics, int maxWidth) {
        String cut = text;
        while (cut.length() > 1 && metrics.stringWidth(cut + TRUNCATED_MARK) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + TRUNCATED_MARK;
    }

    /** Инициалы для аватарки-заглушки: «Иван Петров» → «ИП». */
    private String initials(String name) {
        if (Objects.isNull(name) || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().replace("@", "").split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || result.length() >= 2) {
                break;
            }
            result.append(Character.toUpperCase(part.charAt(0)));
        }
        return result.length() == 0 ? "?" : result.toString();
    }

    private Color avatarColor(String name) {
        int hash = Objects.isNull(name) ? 0 : Math.abs(name.hashCode());
        return AVATAR_COLORS[hash % AVATAR_COLORS.length];
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
