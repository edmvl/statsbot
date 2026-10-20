package ru.zhendozzz.tgbot.messageStatBot.message_executor;

import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.UserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import ru.zhendozzz.tgbot.messageStatBot.app.MyTelegramBot;
import ru.zhendozzz.tgbot.messageStatBot.dto.UserAvatar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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

    private MyTelegramBot tgBot;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public Integer sendMessage(SendMessage sendMessage) {
        try {
            Message execute = tgBot.execute(sendMessage);
            return execute.getMessageId();
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
        }
        return null;
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

            if (files.size() == 1) {
                File file = files.get(0);
                Message sent;
                if (allPhotos) {
                    SendPhoto photo = new SendPhoto();
                    photo.setChatId(chatId);
                    photo.setPhoto(new InputFile(file));
                    photo.setCaption(caption);
                    sent = tgBot.execute(photo);
                } else {
                    SendDocument document = new SendDocument();
                    document.setChatId(chatId);
                    document.setDocument(new InputFile(file));
                    document.setCaption(caption);
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
                    if (first) {
                        media.setCaption(caption);
                        first = false;
                    }
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

            if (messageIds.isEmpty()) {
                return null;
            }
            return messageIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить вложения рекламы в чат {}: {}", chatId, e.getMessage());
            return null;
        }
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

    public void restrictChatMember(Long chatId, Long userId) {
        RestrictChatMember restrictChatMember = new RestrictChatMember();
        restrictChatMember.setChatId(chatId);
        restrictChatMember.setUserId(chatId);
        restrictChatMember.forTimePeriodDuration(Duration.ofHours(1));
        try {
            tgBot.execute(restrictChatMember);
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
        }
    }

    public String getUserStatusInChat(Long chatId, Long userId) {
        GetChatMember chatMemberMember = new GetChatMember();
        chatMemberMember.setUserId(userId);
        chatMemberMember.setChatId(chatId);
        try {
            ChatMember execute = tgBot.execute(chatMemberMember);
            return execute.getStatus();
        } catch (TelegramApiException e) {
            log.error("{}", e.getMessage());
        }
        return null;
    }

    @Override
    public String getUserStatusInChat(String chatId, String userId) {
        return getUserStatusInChat(Long.valueOf(chatId), Long.valueOf(userId));
    }

    @Override
    public ChatMember getUserStatus(String chatId, Long userId) {
        GetChatMember request = new GetChatMember();
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

    @Autowired
    public void setMyTelegramBot(MyTelegramBot tgBot) {
        this.tgBot = tgBot;
    }

}
