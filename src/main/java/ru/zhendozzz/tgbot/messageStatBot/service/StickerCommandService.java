package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.dto.StickerMessage;
import ru.zhendozzz.tgbot.messageStatBot.dto.UserAvatar;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Команда {@code .стикер}: ответом на текстовое сообщение превращает его в стикер
 * и добавляет в общий набор бота. Доступна только администраторам бота
 * (живые админы чата попадают в них автоматически, см. {@link AdminSyncService}).
 *
 * <p>Второй параметр — эмодзи стикера: {@code .стикер 😍}. Без него ставится 🖼.</p>
 *
 * <p>Стикер выглядит как сообщение в приложении: аватар, имя автора со статусом
 * в чате и пузырь с текстом и временем. Сама команда и сообщение-источник
 * остаются в чате, стикер приходит ответом на исходное сообщение.</p>
 */
@Service
@Slf4j
public class StickerCommandService implements CommandExecutable {

    private static final String NO_REPLY = "Ответьте на сообщение, из которого нужно сделать стикер.";
    private static final String NOT_TEXT = "Стикер делаю только из текстовых сообщений.";
    private static final String BAD_EMOJI = "Эмодзи не распознал. Пиши так: .стикер 😍 (эмодзи после команды).";
    /** Эмодзи по умолчанию, если второй параметр не указан. */
    private static final String DEFAULT_EMOJI = "🖼";
    /** Сколько code points допускаем в эмодзи (флаги и ZWJ-последовательности длиннее одного символа). */
    private static final int MAX_EMOJI_CODE_POINTS = 16;

    private static final String STATUS_OWNER = "владелец";
    private static final String STATUS_ADMIN = "админ";
    private static final String STATUS_CHANNEL = "канал";
    private static final String ANONYMOUS = "Аноним";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageExecutor messageExecutor;
    private final ChatAdminService chatAdminService;
    private final StickerImageService stickerImageService;
    private final StickerSetService stickerSetService;

    public StickerCommandService(MessageExecutor messageExecutor,
                                 ChatAdminService chatAdminService,
                                 StickerImageService stickerImageService,
                                 StickerSetService stickerSetService) {
        this.messageExecutor = messageExecutor;
        this.chatAdminService = chatAdminService;
        this.stickerImageService = stickerImageService;
        this.stickerSetService = stickerSetService;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (!isStickerCommand(messageDTO)) {
            return;
        }
        Long chatId = messageDTO.getChatId();
        Long userId = messageDTO.getUserId();
        if (Objects.isNull(chatId) || Objects.isNull(userId) || !isBotAdmin(chatId, userId)) {
            // Не админам не отвечаем: иначе команда превращается в подсказку,
            // как обойти фильтры чата.
            return;
        }

        String emoji = parseEmoji(messageDTO.getUserText());
        if (Objects.isNull(emoji)) {
            messageExecutor.sendMessage(chatId, BAD_EMOJI, messageDTO.getMessageId());
            return;
        }

        Message command = update.getMessage();
        Message replied = Objects.isNull(command) ? null : command.getReplyToMessage();
        if (Objects.isNull(replied)) {
            messageExecutor.sendMessage(chatId, NO_REPLY, messageDTO.getMessageId());
            return;
        }
        String sourceText = replied.getText();
        if (Objects.isNull(sourceText) || sourceText.isBlank()) {
            messageExecutor.sendMessage(chatId, NOT_TEXT, messageDTO.getMessageId());
            return;
        }

        try {
            byte[] png = stickerImageService.render(toStickerMessage(chatId, replied));
            StickerSetService.Result result = stickerSetService.addSticker(png, emoji);
            if (Objects.nonNull(result.getFileId())) {
                messageExecutor.sendSticker(chatId, result.getFileId(), replied.getMessageId());
            } else {
                log.warn("Стикер добавлен в набор {}, но file_id не получен", result.getName());
            }
            stickerSetService.attachToChat(chatId, result.getName());
        } catch (RuntimeException e) {
            log.error("Не удалось сделать стикер в чате {}: {}", chatId, e.getMessage());
            messageExecutor.sendMessage(chatId,
                    "Не удалось добавить стикер в набор: " + e.getMessage(), messageDTO.getMessageId());
        }
    }

    /** Собирает всё, что нужно нарисовать на стикере: автор, статус, время, текст. */
    StickerMessage toStickerMessage(Long chatId, Message replied) {
        User from = replied.getFrom();
        return StickerMessage.builder()
                .authorName(authorName(replied, from))
                .status(status(chatId, replied, from))
                .time(time(replied))
                .text(replied.getText())
                .avatar(avatar(from))
                .build();
    }

    /** Имя автора: имя и фамилия, иначе @username; для анонимного админа — название канала. */
    private String authorName(Message replied, User from) {
        if (Objects.nonNull(replied.getSenderChat()) && Objects.nonNull(replied.getSenderChat().getTitle())) {
            return replied.getSenderChat().getTitle();
        }
        if (Objects.isNull(from)) {
            return ANONYMOUS;
        }
        String name = (nullToEmpty(from.getFirstName()) + " " + nullToEmpty(from.getLastName())).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return Objects.nonNull(from.getUserName()) ? "@" + from.getUserName() : ANONYMOUS;
    }

    /** Статус в чате: владелец/админ по данным Telegram, «канал» для постов от имени канала. */
    private String status(Long chatId, Message replied, User from) {
        if (Objects.nonNull(replied.getSenderChat())) {
            return STATUS_CHANNEL;
        }
        if (Objects.isNull(from) || Objects.isNull(from.getId()) || Boolean.TRUE.equals(from.getIsBot())) {
            return null;
        }
        String chatStatus = chatAdminService.chatStatus(chatId, from.getId());
        if ("creator".equals(chatStatus)) {
            return STATUS_OWNER;
        }
        return "administrator".equals(chatStatus) ? STATUS_ADMIN : null;
    }

    private String time(Message replied) {
        int date = replied.getDate();
        if (date <= 0) {
            return null;
        }
        return LocalTime.ofInstant(Instant.ofEpochSecond(date), ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    /** Аватар автора из Telegram; если не получилось — на стикере будут инициалы. */
    private BufferedImage avatar(User from) {
        if (Objects.isNull(from) || Objects.isNull(from.getId()) || Boolean.TRUE.equals(from.getIsBot())) {
            return null;
        }
        try {
            UserAvatar avatar = messageExecutor.getUserAvatar(from.getId());
            if (Objects.isNull(avatar)) {
                return null;
            }
            String path = messageExecutor.downloadUserAvatar(from.getId(), avatar.fileId());
            return Objects.isNull(path) ? null : ImageIO.read(new File(path));
        } catch (Exception e) {
            log.debug("Аватар для стикера не получен: {}", e.getMessage());
            return null;
        }
    }

    private String nullToEmpty(String value) {
        return Objects.isNull(value) ? "" : value;
    }

    private boolean isStickerCommand(MessageDTO messageDTO) {
        String text = messageDTO.getUserText();
        return Objects.nonNull(text)
                && text.toLowerCase(Locale.ROOT).startsWith(BotCommands.STICKER.getCommand().toLowerCase(Locale.ROOT));
    }

    /**
     * Второй параметр команды — эмодзи стикера: {@code .стикер 😍}.
     *
     * @return эмодзи, {@link #DEFAULT_EMOJI} если параметра нет, {@code null} если вместо
     * эмодзи написан обычный текст
     */
    String parseEmoji(String text) {
        if (Objects.isNull(text)) {
            return DEFAULT_EMOJI;
        }
        String command = BotCommands.STICKER.getCommand();
        String rest = text.length() > command.length() ? text.substring(command.length()).trim() : "";
        if (rest.isEmpty()) {
            return DEFAULT_EMOJI;
        }
        String token = rest.split("\\s+")[0];
        boolean hasLetterOrDigit = token.codePoints().anyMatch(Character::isLetterOrDigit);
        if (hasLetterOrDigit || token.codePointCount(0, token.length()) > MAX_EMOJI_CODE_POINTS) {
            return null;
        }
        return token;
    }

    private boolean isBotAdmin(Long chatId, Long userId) {
        return chatAdminService.isAdmin(chatId, userId);
    }
}
