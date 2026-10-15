package ru.zhendozzz.tgbot.messageStatBot.message_executor;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import ru.zhendozzz.tgbot.messageStatBot.dto.UserAvatar;

import java.io.File;
import java.util.List;

public interface MessageExecutor {

    Integer sendMessage(SendMessage sendMessage);

    Integer sendMessage(String chatId, String text);

    Integer sendMessage(Long chatId, String text);

    Integer sendMessage(String chatId, String text, Integer replyMessageId);

    /**
     * Отправляет вложения с подписью. Возвращает id отправленных сообщений через запятую
     * (для последующего удаления) или {@code null}, если отправить не удалось.
     */
    String sendMediaFiles(String chatId, String caption, List<File> files);

    /**
     * Создаёт ссылку на счёт для оплаты звёздами Telegram (валюта XTR).
     *
     * @param title       заголовок счёта и подпись цены
     * @param description описание счёта
     * @param payload     полезная нагрузка, которая вернётся в {@code successful_payment}
     * @param amount      сумма в звёздах
     * @return ссылка на счёт или {@code null} при ошибке
     */
    String createInvoiceLink(String title, String description, String payload, int amount);

    /** Подтверждает предоплату (нужно успеть в течение 10 секунд). */
    void answerPreCheckoutQuery(String queryId, boolean ok, String errorMessage);

    Integer sendMessage(Long chatId, String text, Integer replyMessageId);

    Integer sendMessageWithoutPreview(String chatId, String text);

    void deleteMessage(Long chatId, Integer messageId);

    void banUser(Long chatId, Long userId);

    void banUser(String chatId, String userId);

    void restrictChatMember(Long chatId, Long userId);

    String getUserStatusInChat(Long chatId, Long userId);

    String getUserStatusInChat(String chatId, String userId);

    /**
     * Возвращает данные аватара пользователя из Telegram (или {@code null}, если фото нет).
     */
    UserAvatar getUserAvatar(Long userId);

    /**
     * Скачивает файл аватара в файловую систему и возвращает путь к файлу
     * (или {@code null} при ошибке).
     */
    String downloadUserAvatar(Long userId, String fileId);

}
