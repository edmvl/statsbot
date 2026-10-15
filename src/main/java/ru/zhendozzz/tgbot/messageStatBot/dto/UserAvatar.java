package ru.zhendozzz.tgbot.messageStatBot.dto;

/**
 * Данные аватара пользователя, полученные из Telegram.
 *
 * @param fileUniqueId стабильный идентификатор файла (для определения смены аватара)
 * @param fileId       идентификатор для скачивания файла
 */
public record UserAvatar(String fileUniqueId, String fileId) {
}
