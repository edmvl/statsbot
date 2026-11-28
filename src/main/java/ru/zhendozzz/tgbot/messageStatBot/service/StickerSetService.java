package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Chat;
import ru.zhendozzz.tgbot.messageStatBot.entity.StickerPack;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.StickerPackRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Наборы стикеров бота (команда {@code .стикер}).
 *
 * <p>Telegram разрешает боту пополнять только наборы, созданные им самим, а имя такого
 * набора обязано оканчиваться на {@code _by_<username бота>}. Поэтому набор один общий
 * на бота ({@code hamoryal_by_hamoryalstatsbot}), а после лимита в 120 статиков
 * создаётся следующий: {@code hamoryal_2_by_...}.</p>
 */
@Service
@Slf4j
public class StickerSetService {

    /**
     * Владелец набора. {@code user_id} должен быть одинаковым при создании набора
     * и при добавлении стикеров — иначе Telegram вернёт STICKERSET_INVALID.
     */
    static final long OWNER_USER_ID = AccessConstants.SUPER_ADMIN_ID;
    /** Потолок статического набора в Telegram. */
    static final int MAX_STICKERS = 120;
    /** Основа имени набора: hamoryal, hamoryal_2, hamoryal_3 … */
    static final String BASE_NAME = "hamoryal";

    private final StickerPackRepo stickerPackRepo;
    private final MessageExecutor messageExecutor;
    private final StickerUploader stickerUploader;
    /** Чаты, к панели стикеров которых набор уже привязан (или чужой набор не перетираем). */
    private final Set<Long> attachedChats = ConcurrentHashMap.newKeySet();

    @Value("${telegram.bot.username}")
    private String botUsername;

    public StickerSetService(StickerPackRepo stickerPackRepo, MessageExecutor messageExecutor,
                             StickerUploader stickerUploader) {
        this.stickerPackRepo = stickerPackRepo;
        this.messageExecutor = messageExecutor;
        this.stickerUploader = stickerUploader;
    }

    /**
     * Добавляет стикер в активный набор, при необходимости создаёт новый.
     *
     * @param png   готовый PNG (512 px по одной из сторон, до 512 КБ)
     * @param emoji эмодзи стикера из второго параметра команды
     * @return какой набор пополнился и был ли он только что создан
     */
    public synchronized Result addSticker(byte[] png, String emoji) {
        StickerPack pack = stickerPackRepo.findFirstByStickersCountLessThanOrderByCreatedAtDesc(MAX_STICKERS);
        if (Objects.isNull(pack)) {
            return createPack(png, emoji);
        }
        String fileId = stickerUploader.addStickerToSet(OWNER_USER_ID, pack.getName(), emoji, png)
                ? messageExecutor.getLastStickerFileId(pack.getName())
                : null;
        if (Objects.isNull(fileId)) {
            throw new IllegalStateException("Telegram не принял стикер в набор " + pack.getName());
        }
        pack.setStickersCount(pack.getStickersCount() + 1);
        stickerPackRepo.save(pack);
        return new Result(pack.getName(), false, pack.getStickersCount(), fileId);
    }

    /**
     * Привязывает набор к панели стикеров чата — только если у чата ещё нет своего набора
     * и бот вправе его менять (супергруппа + админка).
     */
    public void attachToChat(Long chatId, String packName) {
        if (Objects.isNull(chatId) || Objects.isNull(packName) || attachedChats.contains(chatId)) {
            return;
        }
        Chat chat = messageExecutor.getChat(chatId);
        if (Objects.isNull(chat)) {
            return;
        }
        if (Objects.nonNull(chat.getStickerSetName())) {
            attachedChats.add(chatId);
            return;
        }
        if (!Boolean.TRUE.equals(chat.getCanSetStickerSet())) {
            return;
        }
        if (messageExecutor.setChatStickerSet(chatId, packName)) {
            attachedChats.add(chatId);
        }
    }

    /** Имя набора по его номеру: hamoryal_by_…, hamoryal_2_by_… */
    String nameFor(long index) {
        String username = Objects.isNull(botUsername) ? "" : botUsername.replace("@", "").trim();
        if (username.isEmpty()) {
            throw new IllegalStateException("Не задан telegram.bot.username — имя набора стикеров не построить");
        }
        String suffix = "_by_" + username;
        return index <= 1 ? BASE_NAME + suffix : BASE_NAME + "_" + index + suffix;
    }

    private Result createPack(byte[] png, String emoji) {
        long index = stickerPackRepo.count() + 1;
        String name = nameFor(index);
        String title = index <= 1 ? BASE_NAME : BASE_NAME + " " + index;
        String fileId = messageExecutor.createStickerSet(OWNER_USER_ID, name, title, png, emoji);
        if (Objects.isNull(fileId)) {
            throw new IllegalStateException("Telegram не создал набор стикеров " + name);
        }
        StickerPack pack = new StickerPack();
        pack.setName(name);
        pack.setTitle(title);
        pack.setStickersCount(1);
        pack.setCreatedAt(LocalDateTime.now());
        stickerPackRepo.save(pack);
        log.info("Создан набор стикеров {} ({})", name, title);
        return new Result(name, true, 1, fileId);
    }

    /** Что произошло со стикером: куда он попал, был ли набор создан только что и его file_id. */
    public static class Result {

        private final String name;
        private final boolean created;
        private final int count;
        private final String fileId;

        public Result(String name, boolean created, int count, String fileId) {
            this.name = name;
            this.created = created;
            this.count = count;
            this.fileId = fileId;
        }

        public String getName() {
            return name;
        }

        public boolean isCreated() {
            return created;
        }

        public int getCount() {
            return count;
        }

        public String getFileId() {
            return fileId;
        }
    }
}
