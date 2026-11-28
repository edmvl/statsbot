package ru.zhendozzz.tgbot.messageStatBot.message_executor;

import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatStickerSet;
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet;
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet;
import org.telegram.telegrambots.meta.api.methods.stickers.GetStickerSet;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.stickers.InputSticker;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.ChatPermissions;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.UserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberRestricted;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import ru.zhendozzz.tgbot.messageStatBot.app.MyTelegramBot;
import ru.zhendozzz.tgbot.messageStatBot.dto.UserAvatar;
import ru.zhendozzz.tgbot.messageStatBot.service.LogService;
import ru.zhendozzz.tgbot.messageStatBot.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageExecutorImpl implements MessageExecutor {

    private static final int MAX_CAPTION_LENGTH = 1024;
    private static final int MAX_TEXT_CHUNK_LENGTH = 4000;

    /** HTTP-код Telegram на ошибки запроса: для getChatMember это «такого участника нет» и «чат не найден». */
    private static final int BAD_REQUEST = 400;

    /** Статус «человек не в чате» (left/kicked), который проверяет розыгрыш проставляющегося. */
    private static final String STATUS_LEFT = "left";

    /** Эмодзи, обязательный для каждого стикера в наборе. */
    private static final String STICKER_EMOJI = "🖼";
    private static final String STICKER_FILE_NAME = "sticker.png";
    private static final String STICKER_FORMAT_STATIC = "static";
    private static final String STICKER_TYPE_REGULAR = "regular";

    /** Ответ Telegram, когда бота выгнали из чата или чат удалён. */
    private static final String CHAT_NOT_FOUND = "chat not found";

    private MyTelegramBot tgBot;

    private SettingsService settingsService;

    @Autowired
    private LogService logService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public Integer sendMessage(SendMessage sendMessage) {
        try {
            Message execute = tgBot.execute(sendMessage);
            if (Objects.nonNull(execute)) {
                saveBotMessage(sendMessage.getChatId(), execute.getMessageId(), sendMessage.getText());
                return execute.getMessageId();
            }
            return null;
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
            stopMailingsIfChatGone(sendMessage.getChatId(), e);
        }
        return null;
    }

    /**
     * Сообщения бота сохраняем в историю чата (с пометкой {@code bot}),
     * чтобы их было видно в Mini App, но статистика их не считала.
     */
    private void saveBotMessage(String chatId, Integer messageId, String text) {
        if (Objects.isNull(logService)) {
            log.warn("bot-message: LogService не внедрён — сообщение чата {} не сохранено в историю", chatId);
            return;
        }
        logService.saveBotMessage(chatId, messageId, text);
    }

    /**
     * Telegram ответил «chat not found» — чат удалён или бота из него убрали.
     * Помечаем чат недоступным, чтобы ночные рассылки не повторяли ошибку каждый день.
     * Флаг снимается сам, когда из чата снова приходит сообщение.
     */
    private void stopMailingsIfChatGone(String chatId, TelegramApiException e) {
        if (Objects.isNull(chatId) || chatId.isBlank() || !isChatGone(e) || Objects.isNull(settingsService)) {
            return;
        }
        log.warn("Чат {} недоступен (chat not found), рассылки в него остановлены", chatId);
        settingsService.markUnavailable(chatId);
    }

    /** true — Telegram ответил 400 «chat not found», то есть чата больше нет. */
    static boolean isChatGone(TelegramApiException e) {
        if (!(e instanceof TelegramApiRequestException)) {
            return false;
        }
        TelegramApiRequestException request = (TelegramApiRequestException) e;
        if (!Integer.valueOf(BAD_REQUEST).equals(request.getErrorCode())) {
            return false;
        }
        String message = request.getMessage();
        return Objects.nonNull(message)
                && message.toLowerCase(Locale.ROOT).contains(CHAT_NOT_FOUND);
    }

    @Override
    public Integer sendMessage(String chatId, String text, Integer replyMessageId) {
        SendMessage sendMessage = new SendMessage();
        if (Objects.nonNull(replyMessageId)) {
            sendMessage.setReplyToMessageId(replyMessageId);
        }
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        return sendMessage(sendMessage);
    }

    @Override
    public String sendMediaFiles(String chatId, String caption, List<File> files) {
        if (Objects.isNull(files) || files.isEmpty()) {
            return null;
        }
        try {
            boolean allPhotos = files.stream().allMatch(this::isImage);
            List<Integer> messageIds = new ArrayList<>();
            // Telegram разрешает подпись к медиа не длиннее 1024 символов.
            // Длинный текст отправляем отдельным сообщением, чтобы не терять его.
            String mediaCaption = captionForMedia(caption);
            boolean sendTextSeparately = Objects.nonNull(caption) && !caption.isBlank()
                    && Objects.isNull(mediaCaption);

            if (files.size() == 1) {
                File file = files.get(0);
                Message sent;
                if (allPhotos) {
                    SendPhoto photo = new SendPhoto();
                    photo.setChatId(chatId);
                    photo.setPhoto(new InputFile(file));
                    if (Objects.nonNull(mediaCaption) && !mediaCaption.isBlank()) {
                        photo.setCaption(mediaCaption);
                    }
                    sent = tgBot.execute(photo);
                } else {
                    SendDocument document = new SendDocument();
                    document.setChatId(chatId);
                    document.setDocument(new InputFile(file));
                    if (Objects.nonNull(mediaCaption) && !mediaCaption.isBlank()) {
                        document.setCaption(mediaCaption);
                    }
                    sent = tgBot.execute(document);
                }
                if (Objects.nonNull(sent)) {
                    messageIds.add(sent.getMessageId());
                }
            } else {
                SendMediaGroup group = new SendMediaGroup();
                group.setChatId(chatId);
                List<InputMedia> medias = new ArrayList<>();
                boolean first = true;
                for (File file : files) {
                    InputMedia media = allPhotos ? new InputMediaPhoto() : new InputMediaDocument();
                    media.setMedia(file, file.getName());
                    if (first && Objects.nonNull(mediaCaption) && !mediaCaption.isBlank()) {
                        media.setCaption(mediaCaption);
                    }
                    first = false;
                    medias.add(media);
                }
                group.setMedias(medias);
                List<Message> messages = tgBot.execute(group);
                if (Objects.nonNull(messages)) {
                    messages.stream()
                            .map(Message::getMessageId)
                            .forEach(messageIds::add);
                }
            }

            if (sendTextSeparately) {
                for (String part : splitText(caption)) {
                    Integer id = sendMessageWithoutPreview(chatId, part);
                    if (Objects.nonNull(id)) {
                        messageIds.add(id);
                    }
                }
            }

            if (messageIds.isEmpty()) {
                return null;
            }
            if (Objects.nonNull(caption) && !caption.isBlank()) {
                saveBotMessage(chatId, messageIds.get(0), caption);
            }
            return messageIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить вложения рекламы в чат {}: {}", chatId, e.getMessage());
            stopMailingsIfChatGone(chatId, e);
            return null;
        }
    }

    /** Подпись к медиа не должна превышать лимит Telegram. */
    String captionForMedia(String caption) {
        return Objects.nonNull(caption) && caption.length() > MAX_CAPTION_LENGTH ? null : caption;
    }

    /** Делит длинный текст на части, не разрывая слова и по возможности строки. */
    List<String> splitText(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + MAX_TEXT_CHUNK_LENGTH);
            if (end < text.length()) {
                int breakAt = text.lastIndexOf('\n', end);
                if (breakAt <= start) {
                    breakAt = text.lastIndexOf(' ', end);
                }
                if (breakAt > start) {
                    end = breakAt + 1;
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    private boolean isImage(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");
    }

    @Override
    public String createInvoiceLink(String title, String description, String payload, int amount) {
        CreateInvoiceLink invoiceLink = new StarsInvoiceLink(title, description, payload, amount);
        try {
            return tgBot.execute(invoiceLink);
        } catch (TelegramApiException e) {
            log.error("Не удалось создать ссылку на счёт: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Для оплаты звёздами Telegram ({@code XTR}) {@code provider_token} должен быть
     * пустой строкой, а библиотека telegrambots 6.9.x считает его обязательным.
     * Обходим именно эту проверку, остальные поля формируются корректно.
     */
    private static class StarsInvoiceLink extends CreateInvoiceLink {

        StarsInvoiceLink(String title, String description, String payload, int amount) {
            super(title, description, payload, "", "XTR", List.of(new LabeledPrice(title, amount)));
        }

        @Override
        public void validate() {
            // умышленно без проверки providerToken
        }
    }

    @Override
    public void answerPreCheckoutQuery(String queryId, boolean ok, String errorMessage) {
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery(queryId, ok);
        if (!ok) {
            answer.setErrorMessage(errorMessage);
        }
        try {
            tgBot.execute(answer);
        } catch (TelegramApiException e) {
            log.error("Не удалось ответить на pre_checkout_query {}: {}", queryId, e.getMessage());
        }
    }

    @Override
    public Integer sendMessage(Long chatId, String text, Integer replyMessageId) {
        return sendMessage(String.valueOf(chatId), text, replyMessageId);
    }

    @Override
    public Integer sendMessageWithoutPreview(String chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setDisableWebPagePreview(true);
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        return sendMessage(sendMessage);
    }

    @Override
    public Integer sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    @Override
    public Integer sendMessage(Long chatId, String text) {
        return sendMessage(String.valueOf(chatId), text, null);
    }

    @Override
    public void deleteMessage(Long chatId, Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);
        try {
            tgBot.execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
        }
    }

    public void banUser(Long chatId, Long userId) {
        BanChatMember banChatMember = new BanChatMember();
        banChatMember.setChatId(String.valueOf(chatId));
        banChatMember.setUserId(userId);
        try {
            tgBot.execute(banChatMember);
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
        }
    }
    public void banUser(String chatId, String userId) {
        banUser(Long.valueOf(chatId), Long.valueOf(userId));
    }

    @Override
    public boolean unbanUser(Long chatId, Long userId) {
        if (Objects.isNull(chatId) || Objects.isNull(userId)) {
            return false;
        }
        UnbanChatMember unbanChatMember = new UnbanChatMember();
        unbanChatMember.setChatId(String.valueOf(chatId));
        unbanChatMember.setUserId(userId);
        // Не трогаем обычных участников: снимаем только действующий бан.
        unbanChatMember.setOnlyIfBanned(true);
        try {
            return Boolean.TRUE.equals(tgBot.execute(unbanChatMember));
        } catch (TelegramApiException e) {
            log.error("Не удалось снять бан с {} в чате {}: {}", userId, chatId, e.getMessage());
            return false;
        }
    }

    @Override
    public void restrictChatMember(Long chatId, Long userId) {
        RestrictChatMember restrictChatMember = new RestrictChatMember();
        restrictChatMember.setChatId(chatId);
        restrictChatMember.setUserId(userId);
        // Запрет писать сообщения на час: canSendMessages=false без permissions Telegram
        // не применяет — метод требует объект прав целиком.
        restrictChatMember.setPermissions(ChatPermissions.builder()
                .canSendMessages(false)
                .canSendAudios(false)
                .canSendDocuments(false)
                .canSendPhotos(false)
                .canSendVideos(false)
                .canSendVideoNotes(false)
                .canSendVoiceNotes(false)
                .canSendPolls(false)
                .canSendOtherMessages(false)
                .canAddWebPagePreviews(false)
                .canChangeInfo(false)
                .canInviteUsers(false)
                .canPinMessages(false)
                .canManageTopics(false)
                .build());
        restrictChatMember.forTimePeriodDuration(Duration.ofHours(1));
        try {
            tgBot.execute(restrictChatMember);
        } catch (TelegramApiException e) {
            log.error("Не удалось ограничить {} в чате {}: {}", userId, chatId, e.getMessage());
        }
    }

    public String getUserStatusInChat(Long chatId, Long userId) {
        GetChatMember chatMemberMember = new GetChatMember();
        chatMemberMember.setUserId(userId);
        chatMemberMember.setChatId(chatId);
        try {
            ChatMember execute = tgBot.execute(chatMemberMember);
            if (execute instanceof ChatMemberRestricted
                    && !Boolean.TRUE.equals(((ChatMemberRestricted) execute).getIsMember())) {
                // restricted с is_member=false — человек уже вышел или исключён из чата.
                return STATUS_LEFT;
            }
            return execute.getStatus();
        } catch (TelegramApiRequestException e) {
            // 400 — это ответ Telegram по существу (USER_NOT_PARTICIPANT, PARTICIPANT_ID_INVALID,
            // chat not found): проверить членство нельзя, значит участника в чате нет. Иначе
            // розыгрыш «проставляющегося» называл людей, которых в чате уже нет.
            if (Integer.valueOf(BAD_REQUEST).equals(e.getErrorCode())) {
                return STATUS_LEFT;
            }
            log.error("{}", e.getMessage());
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
        }
        return null;
    }

    @Override
    public String getUserStatusInChat(String chatId, String userId) {
        return getUserStatusInChat(Long.valueOf(chatId), Long.valueOf(userId));
    }

    /**
     * Данные чата по username. Пользовательские ники и приватные каналы ботам недоступны —
     * это ожидаемый ответ, поэтому {@code null}, а не исключение.
     */
    @Override
    public Chat getChat(String chatUsername) {
        if (Objects.isNull(chatUsername) || chatUsername.isBlank()) {
            return null;
        }
        GetChat request = new GetChat();
        request.setChatId(chatUsername.startsWith("@") ? chatUsername : "@" + chatUsername);
        try {
            return tgBot.execute(request);
        } catch (TelegramApiException e) {
            log.debug("getChat {}: {}", chatUsername, e.getMessage());
            return null;
        }
    }

    @Override
    public ChatMember getUserStatus(String chatId, Long userId) {        GetChatMember request = new GetChatMember();
        request.setChatId(chatId);
        request.setUserId(userId);
        try {
            return tgBot.execute(request);
        } catch (TelegramApiException e) {
            log.error("Не удалось получить статус {} в чате {}: {}", userId, chatId, e.getMessage());
            return null;
        }
    }

    @Override
    public UserAvatar getUserAvatar(Long userId) {
        try {
            GetUserProfilePhotos request = GetUserProfilePhotos.builder()
                    .userId(userId)
                    .limit(1)
                    .build();
            UserProfilePhotos photos = tgBot.execute(request);
            if (Objects.isNull(photos) || Objects.isNull(photos.getPhotos()) || photos.getPhotos().isEmpty()) {
                return null;
            }
            List<PhotoSize> sizes = photos.getPhotos().get(0);
            PhotoSize largest = sizes.get(sizes.size() - 1);
            return new UserAvatar(largest.getFileUniqueId(), largest.getFileId());
        } catch (Exception e) {
            log.error("Не удалось получить аватар пользователя {}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public String downloadUserAvatar(Long userId, String fileId) {
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File file = tgBot.execute(getFile);

            File dir = new File(uploadDir, "avatars");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File target = new File(dir, userId + ".jpg");
            tgBot.downloadFile(file, target);
            return target.getPath();
        } catch (Exception e) {
            log.error("Не удалось скачать аватар пользователя {}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public void sendSticker(Long chatId, String stickerFileId, Integer replyToMessageId) {
        SendSticker sendSticker = new SendSticker();
        sendSticker.setChatId(chatId);
        sendSticker.setSticker(new InputFile(stickerFileId));
        if (Objects.nonNull(replyToMessageId)) {
            sendSticker.setReplyToMessageId(replyToMessageId);
        }
        try {
            tgBot.execute(sendSticker);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить стикер в чат {}: {}", chatId, e.getMessage());
        }
    }

    @Override
    public String createStickerSet(long ownerUserId, String name, String title, byte[] png, String emoji) {
        String stickerEmoji = Objects.isNull(emoji) || emoji.isBlank() ? STICKER_EMOJI : emoji;
        CreateNewStickerSet request = CreateNewStickerSet.builder()
                .userId(ownerUserId)
                .name(name)
                .title(title)
                .sticker(sticker(png, stickerEmoji))
                .stickerFormat(STICKER_FORMAT_STATIC)
                .stickerType(STICKER_TYPE_REGULAR)
                // Поле устарело, но библиотека отправляет его в multipart всегда:
                // с null запрос разваливается, а Telegram значение игнорирует.
                .emojis(stickerEmoji)
                .build();
        try {
            tgBot.execute(request);
        } catch (TelegramApiException e) {
            log.error("Не удалось создать набор стикеров {}: {}", name, e.getMessage());
            return null;
        }
        return lastStickerFileId(name);
    }

    @Override
    public String addStickerToSet(long ownerUserId, String name, byte[] png, String emoji) {
        String stickerEmoji = Objects.isNull(emoji) || emoji.isBlank() ? STICKER_EMOJI : emoji;
        AddStickerToSet request = new AddStickerToSet();
        request.setUserId(ownerUserId);
        request.setName(name);
        request.setSticker(sticker(png, stickerEmoji));
        try {
            tgBot.execute(request);
        } catch (TelegramApiException e) {
            log.error("Не удалось добавить стикер в набор {}: {}", name, e.getMessage());
            return null;
        }
        return lastStickerFileId(name);
    }

    /**
     * Telegram добавляет стикер в конец набора, поэтому берём последний: его {@code file_id}
     * можно отправлять в чат как есть, не загружая PNG повторно.
     */
    private String lastStickerFileId(String name) {
        try {
            GetStickerSet request = new GetStickerSet();
            request.setName(name);
            StickerSet stickerSet = tgBot.execute(request);
            if (Objects.isNull(stickerSet) || Objects.isNull(stickerSet.getStickers())
                    || stickerSet.getStickers().isEmpty()) {
                return null;
            }
            List<Sticker> stickers = stickerSet.getStickers();
            return stickers.get(stickers.size() - 1).getFileId();
        } catch (TelegramApiException e) {
            log.error("Не удалось прочитать набор стикеров {}: {}", name, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean setChatStickerSet(Long chatId, String packName) {
        SetChatStickerSet request = new SetChatStickerSet();
        request.setChatId(chatId);
        request.setStickerSetName(packName);
        try {
            tgBot.execute(request);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Не удалось привязать набор {} к чату {}: {}", packName, chatId, e.getMessage());
            return false;
        }
    }

    @Override
    public Chat getChat(Long chatId) {
        if (Objects.isNull(chatId)) {
            return null;
        }
        GetChat request = new GetChat();
        request.setChatId(chatId.toString());
        try {
            return tgBot.execute(request);
        } catch (TelegramApiException e) {
            log.debug("getChat {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    private InputSticker sticker(byte[] png, String emoji) {
        return InputSticker.builder()
                .sticker(stickerFile(png))
                .emoji(emoji)
                .build();
    }

    private InputFile stickerFile(byte[] png) {
        return new InputFile(new ByteArrayInputStream(png), STICKER_FILE_NAME);
    }

    @Autowired
    public void setMyTelegramBot(MyTelegramBot tgBot) {
        this.tgBot = tgBot;
    }

    @Autowired
    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Autowired
    public void setLogService(LogService logService) {
        this.logService = logService;
    }

}
