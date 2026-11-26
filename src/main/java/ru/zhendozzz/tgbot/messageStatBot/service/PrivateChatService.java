package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.PrivateChatDto;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Личные чаты с ботом для супер-админа: список переписок и сообщения внутри одной.
 *
 * <p>Сообщения из лички попадают в {@code log} тем же путём, что и сообщения групп
 * ({@link LogService}), только {@code chat_id} у них положительный — id пользователя.
 * Поэтому отдельной таблицы не нужно, а имя автора берётся из {@code users}.</p>
 */
@Service
public class PrivateChatService {

    /** Приватный чат: положительный числовой id (у групп и каналов id отрицательный). */
    private static final Pattern PRIVATE_CHAT_ID = Pattern.compile("\\d{1,19}");

    private final LogRepo logRepo;
    private final LogService logService;

    public PrivateChatService(LogRepo logRepo, LogService logService) {
        this.logRepo = logRepo;
        this.logService = logService;
    }

    /** Переписки с ботом, свежие сверху. */
    public List<PrivateChatDto> findChats() {
        return logRepo.findPrivateChats();
    }

    /** Страница сообщений переписки (новые сверху, как в истории чатов). */
    public List<ChatMessageDto> findMessages(String chatId, int page, int size) {
        return logService.findByChatId(chatId, page, size);
    }

    /** Приводит id из URL к виду, который вообще может быть личным чатом. */
    public String normalizeChatId(String chatId) {
        if (Objects.isNull(chatId)) {
            return null;
        }
        String trimmed = chatId.trim();
        return PRIVATE_CHAT_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    /** Заголовок страницы переписки: «Имя Фамилия (ник)» или «ID 123». */
    public String findTitle(String chatId) {
        String title = logService.findChatName(chatId);
        if (Objects.nonNull(title) && !title.isBlank()) {
            return title;
        }
        List<String> names = logRepo.findUserNameByUserId(chatId);
        if (Objects.nonNull(names) && !names.isEmpty()) {
            String name = names.get(0);
            if (Objects.nonNull(name) && !name.isBlank()) {
                return name.trim();
            }
        }
        return "ID " + chatId;
    }
}
