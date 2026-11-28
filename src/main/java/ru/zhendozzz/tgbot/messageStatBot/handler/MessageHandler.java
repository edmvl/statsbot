package ru.zhendozzz.tgbot.messageStatBot.handler;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.FileDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.service.NeedProcessMessage;
import ru.zhendozzz.tgbot.messageStatBot.service.CommandExecutable;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.BotCommands;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.FileTypes;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.HoroscopeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Video;
import org.telegram.telegrambots.meta.api.objects.VideoNote;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.games.Animation;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class MessageHandler implements Handler<Update> {

    private final List<CommandExecutable> commandExecutables;
    private final List<NeedProcessMessage> needProcessMessages;
    @Value("${telegram.bot.username}")
    private String botUsername;

    @Lazy
    public MessageHandler(
            List<CommandExecutable> commandExecutables,
            List<NeedProcessMessage> needProcessMessages
    ) {
        this.commandExecutables = commandExecutables;
        this.needProcessMessages = needProcessMessages;
    }

    @Override
    public void choose(Update update) {
        Message message = update.getMessage();
        // Пользователь может отредактировать сообщение — Telegram присылает его как
        // edited_message (а не message). Обрабатываем так же, но без запуска команд.
        boolean edited = false;
        if (Objects.isNull(message)) {
            message = update.getEditedMessage();
            edited = Objects.nonNull(message);
        }
        if (Objects.isNull(message)) {
            return;
        }
        User from = message.getFrom();
        // Свои сообщения бот не должен учитывать: в частности, после бана Telegram
        // присылает служебное сообщение left_chat_member от имени самого бота.
        if (isOwnBot(from)) {
            log.debug("Пропускаем сообщение самого бота в чате {}", message.getChatId());
            return;
        }

        boolean serviceMessage = isServiceMessage(message);
        FileDto file = serviceMessage ? null : getFile(message);
        MessageDTO messageDTO = MessageDTO.builder()
                .chatId(message.getChatId())
                .chatName(Objects.nonNull(message.getChat()) ? message.getChat().getTitle() : null)
                .dateTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(message.getDate()), ZoneId.systemDefault()))
                .messageId(message.getMessageId())
                .replyMessageId(Objects.nonNull(message.getReplyToMessage()) ? message.getReplyToMessage().getMessageId() : null)
                .userId(Objects.nonNull(from) ? from.getId() : null)
                .userName(Objects.nonNull(from) ? from.getUserName() : null)
                .userFirstName(Objects.nonNull(from) ? from.getFirstName() : null)
                .userLastName(Objects.nonNull(from) ? from.getLastName() : null)
                .userText(resolveText(message, serviceMessage))
                .file(file)
                .serviceMessage(serviceMessage)
                .edited(edited)
                .editDateTime(edited && Objects.nonNull(message.getEditDate())
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(message.getEditDate()), ZoneId.systemDefault())
                        : null)
                .build();
        // Служебные события не являются командами и не должны запускать обработчики команд.
        // Команды из отредактированных сообщений тоже не выполняем.
        if (!serviceMessage && !edited
                && getAllBotCommands().stream().anyMatch(s -> messageDTO.getUserText().toLowerCase(Locale.ROOT).startsWith(s.toLowerCase()))) {
            commandExecutables.forEach(service -> service.executeCommand(messageDTO, update));
        }
        needProcessMessages.forEach(service -> service.processMessage(messageDTO, update));
    }

    /** Сообщение отправлено самим ботом (а не другим ботом или анонимным админом). */
    private boolean isOwnBot(User from) {
        if (Objects.isNull(from) || !Boolean.TRUE.equals(from.getIsBot()) || Objects.isNull(botUsername)) {
            return false;
        }
        String userName = from.getUserName();
        return Objects.nonNull(userName)
                && botUsername.replace("@", "").equalsIgnoreCase(userName.replace("@", ""));
    }

    private boolean isServiceMessage(Message message) {
        List<User> newMembers = message.getNewChatMembers();
        return (Objects.nonNull(newMembers) && !newMembers.isEmpty())
                || Objects.nonNull(message.getLeftChatMember())
                || Objects.nonNull(message.getNewChatTitle())
                || (Objects.nonNull(message.getNewChatPhoto()) && !message.getNewChatPhoto().isEmpty())
                || Boolean.TRUE.equals(message.getDeleteChatPhoto())
                || Boolean.TRUE.equals(message.getGroupchatCreated())
                || Boolean.TRUE.equals(message.getSuperGroupCreated())
                || Boolean.TRUE.equals(message.getChannelChatCreated())
                || Objects.nonNull(message.getPinnedMessage())
                || Objects.nonNull(message.getMigrateToChatId())
                || Objects.nonNull(message.getMigrateFromChatId())
                || Objects.nonNull(message.getProximityAlertTriggered())
                || Objects.nonNull(message.getVideoChatStarted())
                || Objects.nonNull(message.getVideoChatEnded())
                || Objects.nonNull(message.getVideoChatParticipantsInvited())
                || Objects.nonNull(message.getVideoChatScheduled());
    }

    /**
     * Текст для истории чата. Обычные сообщения сохраняются как есть (без упоминания бота),
     * а служебные события — человекочитаемым описанием произошедшего.
     */
    private String resolveText(Message message, boolean serviceMessage) {
        if (message.hasText()) {
            return String.join("", message.getText().split("@" + botUsername));
        }
        if (!serviceMessage) {
            return "";
        }

        List<User> newMembers = message.getNewChatMembers();
        if (Objects.nonNull(newMembers) && !newMembers.isEmpty()) {
            String names = newMembers.stream().map(this::userLabel).collect(Collectors.joining(", "));
            if (newMembers.size() == 1 && isSameUser(message.getFrom(), newMembers.get(0))) {
                return "Вступил(а) в чат";
            }
            return Objects.nonNull(message.getFrom())
                    ? "Добавил(а) в чат: " + names
                    : "В чат добавлен(ы): " + names;
        }

        User left = message.getLeftChatMember();
        if (Objects.nonNull(left)) {
            if (isSameUser(message.getFrom(), left)) {
                return "Покинул(а) чат";
            }
            return Objects.nonNull(message.getFrom())
                    ? "Удалил(а) из чата: " + userLabel(left)
                    : "Из чата удалён(ы): " + userLabel(left);
        }

        if (Objects.nonNull(message.getNewChatTitle())) {
            return "Изменил(а) название чата: «" + message.getNewChatTitle() + "»";
        }
        if (Objects.nonNull(message.getNewChatPhoto()) && !message.getNewChatPhoto().isEmpty()) {
            return "Изменил(а) фото чата";
        }
        if (Boolean.TRUE.equals(message.getDeleteChatPhoto())) {
            return "Удалил(а) фото чата";
        }
        if (Objects.nonNull(message.getPinnedMessage())) {
            return "Закрепил(а) сообщение";
        }
        if (Boolean.TRUE.equals(message.getGroupchatCreated())
                || Boolean.TRUE.equals(message.getSuperGroupCreated())
                || Boolean.TRUE.equals(message.getChannelChatCreated())) {
            return "Создал(а) чат";
        }
        return "Служебное сообщение";
    }

    private boolean isSameUser(User first, User second) {
        return Objects.nonNull(first) && Objects.nonNull(second)
                && Objects.equals(first.getId(), second.getId());
    }

    private String userLabel(User user) {
        if (Objects.isNull(user)) {
            return "пользователь";
        }
        String firstName = Objects.toString(user.getFirstName(), "").trim();
        String lastName = Objects.toString(user.getLastName(), "").trim();
        String name = (firstName + " " + lastName).trim();
        String userName = user.getUserName();
        if (!name.isEmpty() && Objects.nonNull(userName) && !userName.isEmpty()) {
            return name + " (@" + userName + ")";
        }
        if (!name.isEmpty()) {
            return name;
        }
        if (Objects.nonNull(userName) && !userName.isEmpty()) {
            return "@" + userName;
        }
        return String.valueOf(user.getId());
    }

    private List<String> getAllBotCommands() {
        List<String> s1 = Arrays.stream(BotCommands.values()).map(BotCommands::getCommand).toList();
        List<String> s2 = Arrays.stream(HoroscopeEnum.values()).map(HoroscopeEnum::getName).toList();
        return Stream.concat(s1.stream(), s2.stream()).toList();
    }

    private FileDto getFile(Message message) {
        List<PhotoSize> photo = message.getPhoto();
        if (Objects.nonNull(photo)) {
            Optional<PhotoSize> max = photo.stream().max(Comparator.comparingInt(PhotoSize::getFileSize));
            return new FileDto(FileTypes.PHOTO, max.map(PhotoSize::getFileId).orElse(null), null);
        }
        Document document = message.getDocument();
        if (Objects.nonNull(document)) {
            return new FileDto(FileTypes.DOCUMENT, document.getFileId(), document.getFileName());
        }
        Sticker sticker = message.getSticker();
        if (Objects.nonNull(sticker)) {
            return new FileDto(FileTypes.STICKER, sticker.getFileId(), null);
        }
        VideoNote videoNote = message.getVideoNote();
        if (Objects.nonNull(videoNote)) {
            return new FileDto(FileTypes.VIDEO_NOTE, videoNote.getFileId(), null);
        }
        Video video = message.getVideo();
        if (Objects.nonNull(video)) {
            return new FileDto(FileTypes.VIDEO, video.getFileId(), video.getFileName());
        }
        Voice voice = message.getVoice();
        if (Objects.nonNull(voice)) {
            return new FileDto(FileTypes.VOICE, voice.getFileId(), null);
        }
        Animation animation = message.getAnimation();
        if (Objects.nonNull(animation)) {
            return new FileDto(FileTypes.ANIMATION, animation.getFileId(), animation.getFileName());
        }
        Audio audio = message.getAudio();
        if (Objects.nonNull(audio)) {
            return new FileDto(FileTypes.AUDIO, audio.getFileId(), audio.getFileName());
        }
        return null;
    }

}
