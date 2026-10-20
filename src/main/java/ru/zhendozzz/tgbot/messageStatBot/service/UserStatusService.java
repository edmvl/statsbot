package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberBanned;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberRestricted;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.UserRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Команда {@code .статус} — проверяет статус пользователя в канале поездок
 * через Telegram {@code getChatMember} (единственный способ узнать бан по API).
 *
 * <p>Пользователя можно указать id, {@code @ником} или ответом на его сообщение.</p>
 */
@Slf4j
@Service
public class UserStatusService implements CommandExecutable {

    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"));

    private final MessageExecutor messageExecutor;
    private final SettingsService settingsService;
    private final UserRepo userRepo;
    private final LogRepo logRepo;

    public UserStatusService(
            MessageExecutor messageExecutor,
            SettingsService settingsService,
            UserRepo userRepo,
            LogRepo logRepo
    ) {
        this.messageExecutor = messageExecutor;
        this.settingsService = settingsService;
        this.userRepo = userRepo;
        this.logRepo = logRepo;
    }

    @Override
    public void executeCommand(MessageDTO messageDTO, Update update) {
        if (Objects.isNull(messageDTO.getUserText())
                || !messageDTO.getUserText().startsWith(BotCommands.STATUS.getCommand())) {
            return;
        }
        messageExecutor.deleteMessage(messageDTO.getChatId(), messageDTO.getMessageId());

        String tripChatId = settingsService.findTripChannelId();
        if (Objects.isNull(tripChatId)) {
            reply(messageDTO, "❌ Не настроен канал поездок.");
            return;
        }

        Long targetId = resolveTarget(messageDTO, update);
        if (Objects.isNull(targetId)) {
            reply(messageDTO, "ℹ️ Укажите пользователя: `.статус 123456789`, `.статус @ник` "
                    + "или ответьте командой на его сообщение.");
            return;
        }

        ChatMember member = messageExecutor.getUserStatus(tripChatId, targetId);
        reply(messageDTO, buildText(targetId, tripChatId, member));
    }

    private void reply(MessageDTO messageDTO, String text) {
        messageExecutor.sendMessage(messageDTO.getChatId(), text);
    }

    /** Ответ на сообщение, числовой id или {@code @ник} (ищется в таблице users). */
    private Long resolveTarget(MessageDTO messageDTO, Update update) {
        Message reply = update.getMessage() == null ? null : update.getMessage().getReplyToMessage();
        if (Objects.nonNull(reply) && Objects.nonNull(reply.getFrom())) {
            return reply.getFrom().getId();
        }

        String argument = messageDTO.getUserText()
                .substring(BotCommands.STATUS.getCommand().length())
                .trim();
        if (argument.isEmpty()) {
            return null;
        }
        if (argument.matches("\\d{3,}")) {
            try {
                return Long.valueOf(argument);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String userName = argument.startsWith("@") ? argument.substring(1) : argument;
        return userRepo.findFirstByUserNameIgnoreCase(userName)
                .map(user -> parseId(user.getId()))
                .orElse(null);
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildText(Long userId, String tripChatId, ChatMember member) {
        String chatName = logRepo.findChatNameByChatId(tripChatId);
        String chat = Objects.isNull(chatName) || chatName.isEmpty() ? "канал поездок" : "«" + chatName + "»";

        StringBuilder text = new StringBuilder();
        text.append("👤 ").append(displayName(userId)).append(" · ID ").append(userId).append('\n');
        text.append("📍 Чат: ").append(chat).append('\n');
        text.append(statusLine(member));
        return text.toString();
    }

    private String statusLine(ChatMember member) {
        if (Objects.isNull(member)) {
            return "❔ Не удалось получить статус (бот не в чате или пользователь не найден).";
        }
        switch (member.getStatus()) {
            // Владельца и администраторов не раскрываем: для проверки бана это лишнее,
            // а статус в чате — конфиденциальная информация.
            case "creator":
            case "administrator":
            case "member":
                return "✅ Участник";
            case "restricted":
                return "⏳ Ограничен" + until(((ChatMemberRestricted) member).getUntilDate());
            case "left":
                return "🚪 Не в чате (вышел или не состоял)";
            case "kicked":
                return "🚫 Заблокирован" + until(((ChatMemberBanned) member).getUntilDate());
            default:
                return "❔ Статус: " + member.getStatus();
        }
    }

    private String until(Integer untilDate) {
        if (Objects.isNull(untilDate) || untilDate == 0) {
            return " (бессрочно)";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(untilDate), ZoneId.systemDefault());
        return " до " + UNTIL_FORMAT.format(dateTime);
    }

    private String displayName(Long userId) {
        return userRepo.findById(String.valueOf(userId))
                .map(user -> {
                    String name = (Objects.toString(user.getFirstName(), "") + " "
                            + Objects.toString(user.getLastName(), "")).trim();
                    String userName = user.getUserName();
                    if (!name.isEmpty() && Objects.nonNull(userName) && !userName.isEmpty()) {
                        return name + " (@" + userName + ")";
                    }
                    if (!name.isEmpty()) {
                        return name;
                    }
                    return Objects.nonNull(userName) && !userName.isEmpty() ? "@" + userName : String.valueOf(userId);
                })
                .orElse(String.valueOf(userId));
    }
}
