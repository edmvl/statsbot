package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.util.Locale;
import java.util.Objects;

/**
 * Команда {@code .стикер}: ответом на текстовое сообщение превращает его в стикер
 * и добавляет в общий набор бота. Доступна только администраторам бота
 * (живые админы чата попадают в них автоматически, см. {@link AdminSyncService}).
 *
 * <p>Сама команда и сообщение-источник остаются в чате, стикер приходит ответом
 * на исходное сообщение.</p>
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

    private final MessageExecutor messageExecutor;
    private final SettingsService settingsService;
    private final StickerImageService stickerImageService;
    private final StickerSetService stickerSetService;

    public StickerCommandService(MessageExecutor messageExecutor,
                                 SettingsService settingsService,
                                 StickerImageService stickerImageService,
                                 StickerSetService stickerSetService) {
        this.messageExecutor = messageExecutor;
        this.settingsService = settingsService;
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
            byte[] png = stickerImageService.render(sourceText);
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
        return AccessConstants.SUPER_ADMIN_ID.equals(userId) || settingsService.isUserAdmin(chatId, userId);
    }
}
