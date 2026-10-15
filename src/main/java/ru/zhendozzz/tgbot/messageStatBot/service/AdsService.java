package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.dto.AdsFileView;
import ru.zhendozzz.tgbot.messageStatBot.entity.Ads;
import ru.zhendozzz.tgbot.messageStatBot.entity.AdsFile;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.AdsFileRepository;
import ru.zhendozzz.tgbot.messageStatBot.repo.AdsRepository;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.KeyboardUtils;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.AdStatus;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdsService {

    /** К посту можно прикрепить не больше трёх файлов. */
    public static final int MAX_FILES = 3;

    private static final String ADS_DIR = "ads";
    private static final String APP_BASE_URL = "https://api.zhendozzz.ru";
    private static final String PAYLOAD_PREFIX = "ad:";

    /** Статусы, которые видны супер-админу на странице рекламы. */
    private static final List<String> MODERATION_STATUSES =
            List.of(AdStatus.ACTIVE, AdStatus.PENDING, AdStatus.APPROVED);

    /** Статусы предложений, которые видит пользователь на своей странице. */
    private static final List<String> USER_PROPOSAL_STATUSES =
            List.of(AdStatus.PENDING, AdStatus.APPROVED, AdStatus.ACTIVE, AdStatus.REJECTED);

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final MessageExecutor messageExecutor;
    private final AdsRepository adsRepository;
    private final AdsFileRepository adsFileRepository;
    private final SettingsService settingsService;

    public AdsService(MessageExecutor messageExecutor, AdsRepository adsRepository,
                      AdsFileRepository adsFileRepository, SettingsService settingsService) {
        this.messageExecutor = messageExecutor;
        this.adsRepository = adsRepository;
        this.adsFileRepository = adsFileRepository;
        this.settingsService = settingsService;
    }

    // --- публикация ---------------------------------------------------------

    /**
     * Поднимает активные посты вниз чата (удаляет прошлое сообщение и публикует заново)
     * и убирает посты, период показа которых закончился.
     */
    public void sendAds() {
        LocalDateTime now = LocalDateTime.now();
        for (Ads ad : adsRepository.findByStatus(AdStatus.ACTIVE)) {
            if (!adsEnabled(ad.getChatId())) {
                // реклама в чате отключена настройкой — убираем опубликованный пост
                removeIfPublished(ad);
            } else if (isActive(ad, now)) {
                deleteLastMessages(ad);
                publish(ad);
            } else if (isFinished(ad, now)) {
                removeIfPublished(ad);
            }
        }
    }

    /**
     * Частая сверка состояния постов: публикует те, у которых начался период показа
     * и которых ещё нет в чате, и убирает те, у которых период закончился.
     */
    public void reconcileAds() {
        LocalDateTime now = LocalDateTime.now();
        for (Ads ad : adsRepository.findByStatus(AdStatus.ACTIVE)) {
            if (!adsEnabled(ad.getChatId())) {
                removeIfPublished(ad);
            } else if (isActive(ad, now)) {
                if (!hasPublishedMessage(ad)) {
                    publish(ad);
                }
            } else if (isFinished(ad, now)) {
                removeIfPublished(ad);
            }
        }
    }

    /** В чате должна быть включена настройка «Показывать рекламу». */
    public boolean adsEnabled(String chatId) {
        return settingsService.isEnabled(chatId, ChatSettingConstants.ENABLE_ADS);
    }

    private void publish(Ads ad) {
        List<File> files = findFiles(ad.getId());
        String lastMessage;
        if (files.isEmpty()) {
            Integer messageId = messageExecutor.sendMessageWithoutPreview(ad.getChatId(), ad.getMessage());
            lastMessage = Objects.isNull(messageId) ? null : String.valueOf(messageId);
        } else {
            lastMessage = messageExecutor.sendMediaFiles(ad.getChatId(), ad.getMessage(), files);
        }
        // Если отправить не удалось — не сохраняем «битую» ссылку, чтобы пост ушёл при следующем запуске.
        ad.setLastMessage(lastMessage);
        if (Objects.isNull(lastMessage)) {
            log.warn("Не удалось опубликовать рекламный пост {} в чате {}", ad.getId(), ad.getChatId());
        }
        adsRepository.save(ad);
    }

    private boolean isActive(Ads ads, LocalDateTime now) {
        boolean started = Objects.isNull(ads.getDateStart()) || !now.isBefore(ads.getDateStart());
        boolean notEnded = Objects.isNull(ads.getDateEnd()) || !now.isAfter(ads.getDateEnd());
        return started && notEnded;
    }

    private boolean isFinished(Ads ads, LocalDateTime now) {
        return Objects.nonNull(ads.getDateEnd()) && now.isAfter(ads.getDateEnd());
    }

    private boolean hasPublishedMessage(Ads ads) {
        return Objects.nonNull(ads.getLastMessage()) && !ads.getLastMessage().isEmpty();
    }

    private void removeIfPublished(Ads ad) {
        if (!hasPublishedMessage(ad)) {
            return;
        }
        deleteLastMessages(ad);
        ad.setLastMessage(null);
        adsRepository.save(ad);
    }

    /**
     * Удаляет сообщения последней публикации. В поле {@code lastMessage} лежат id
     * отправленных сообщений через запятую (альбом даёт несколько id).
     */
    private void deleteLastMessages(Ads ads) {
        if (!hasPublishedMessage(ads)) {
            return;
        }
        Long chatId = parseChatId(ads.getChatId());
        if (Objects.isNull(chatId)) {
            log.warn("Не удалось определить числовой id чата {} для удаления рекламы {}",
                    ads.getChatId(), ads.getId());
            return;
        }
        for (String part : ads.getLastMessage().split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                messageExecutor.deleteMessage(chatId, Integer.valueOf(trimmed));
            } catch (Exception e) {
                log.error("Не удалось удалить сообщение {} рекламного поста {}: {}", trimmed, ads.getId(), e.getMessage());
            }
        }
    }

    private Long parseChatId(String chatId) {
        try {
            return Objects.isNull(chatId) ? null : Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- CRUD ---------------------------------------------------------------

    /** Посты для страницы супер-админа: обычная реклама и предложения пользователей. */
    public List<Ads> getAllAds() {
        List<Ads> all = adsRepository.findByStatusInOrderByIdDesc(MODERATION_STATUSES);
        all.forEach(this::fillFiles);
        return all;
    }

    public Ads getAdById(Long id) {
        Ads ad = adsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ad not found"));
        fillFiles(ad);
        return ad;
    }

    public Ads saveAd(Ads ad) {
        if (Objects.isNull(ad.getStatus())) {
            ad.setStatus(AdStatus.ACTIVE);
        }
        Ads saved = adsRepository.save(ad);
        fillFiles(saved);
        return saved;
    }

    /**
     * Обновляет редактируемые поля поста, сохраняя служебное поле {@code lastMessage}.
     * Если пост переехал в другой чат, прошлое сообщение удаляется, а сам пост
     * сразу приводится в соответствие с новыми условиями показа.
     */
    public Ads updateAd(Long id, Ads source) {
        Ads existing = getAdById(id);
        if (!AdStatus.ACTIVE.equals(existing.getStatus())) {
            throw new IllegalStateException("Редактировать можно только опубликованную рекламу");
        }
        if (!Objects.equals(existing.getChatId(), source.getChatId())) {
            // прошлый пост мог быть в другом чате — убираем его оттуда
            deleteLastMessages(existing);
            existing.setLastMessage(null);
        }
        existing.setChatId(source.getChatId());
        existing.setMessage(source.getMessage());
        existing.setDateStart(source.getDateStart());
        existing.setDateEnd(source.getDateEnd());
        Ads saved = adsRepository.save(existing);

        LocalDateTime now = LocalDateTime.now();
        if (isActive(saved, now)) {
            // текст, вложения или период могли поменяться — обновляем пост в чате сразу
            deleteLastMessages(saved);
            publish(saved);
        } else if (isFinished(saved, now)) {
            removeIfPublished(saved);
        }
        fillFiles(saved);
        return saved;
    }

    public void deleteAd(Long id) {
        Ads ad = getAdById(id);
        deleteLastMessages(ad);
        adsFileRepository.findByAdsIdOrderByIdAsc(id).forEach(adsFileRepository::delete);
        deleteFilesFromDisk(id);
        adsRepository.deleteById(id);
    }

    // --- предложения пользователей ------------------------------------------

    /**
     * Создаёт предложение рекламы от пользователя. Оно попадёт в чат только
     * после одобрения супер-админом и оплаты звёздами.
     */
    public Ads createProposal(String userId, String chatId, String message, Integer days, List<MultipartFile> files) {
        if (isBlank(userId)) {
            throw new IllegalArgumentException("Не удалось определить пользователя");
        }
        if (isBlank(chatId)) {
            throw new IllegalArgumentException("Выберите чат");
        }
        if (!adsEnabled(chatId.trim())) {
            throw new IllegalArgumentException("В этом чате реклама не показывается");
        }
        if (isBlank(message)) {
            throw new IllegalArgumentException("Введите текст сообщения");
        }
        int requestedDays = Objects.isNull(days) ? 1 : days;
        if (requestedDays < 1 || requestedDays > AdStatus.MAX_DAYS) {
            throw new IllegalArgumentException("Количество дней: от 1 до " + AdStatus.MAX_DAYS);
        }
        long active = adsRepository.countByUserIdAndStatusIn(userId.trim(),
                List.of(AdStatus.PENDING, AdStatus.APPROVED));
        if (active >= AdStatus.MAX_PENDING_PER_USER) {
            throw new IllegalArgumentException("У вас уже есть предложения на модерации или на оплате");
        }
        long fileCount = Objects.isNull(files) ? 0 : files.stream()
                .filter(file -> Objects.nonNull(file) && !file.isEmpty())
                .count();
        if (fileCount > MAX_FILES) {
            throw new IllegalArgumentException("К посту можно прикрепить не больше " + MAX_FILES + " файлов");
        }

        Ads ad = new Ads();
        ad.setChatId(chatId.trim());
        ad.setMessage(message.trim());
        ad.setUserId(userId.trim());
        ad.setDays(requestedDays);
        ad.setStatus(AdStatus.PENDING);
        ad.setCreatedAt(LocalDateTime.now());
        Ads saved = adsRepository.save(ad);
        uploadFiles(saved.getId(), files);
        fillFiles(saved);
        notifySuperAdmin(saved);
        return saved;
    }

    /** Предложения конкретного пользователя для его страницы. */
    public List<Ads> getUserProposals(String userId) {
        List<Ads> ads = adsRepository.findByUserIdAndStatusInOrderByIdDesc(userId, USER_PROPOSAL_STATUSES);
        ads.forEach(this::fillFiles);
        return ads;
    }

    public Ads getUserProposal(Long id, String userId) {
        Ads ad = getAdById(id);
        if (!Objects.equals(ad.getUserId(), userId)) {
            throw new RuntimeException("Proposal not found");
        }
        return ad;
    }

    public Ads approveProposal(Long id) {
        Ads ad = getAdById(id);
        if (!AdStatus.PENDING.equals(ad.getStatus())) {
            throw new IllegalStateException("Предложение уже обработано");
        }
        ad.setStatus(AdStatus.APPROVED);
        Ads saved = adsRepository.save(ad);
        fillFiles(saved);
        notifyProposer(saved, "Ваше предложение рекламы одобрено. Осталось оплатить "
                + priceOf(saved) + " ⭐.");
        return saved;
    }

    public Ads rejectProposal(Long id) {
        Ads ad = getAdById(id);
        if (!AdStatus.PENDING.equals(ad.getStatus())) {
            throw new IllegalStateException("Предложение уже обработано");
        }
        ad.setStatus(AdStatus.REJECTED);
        Ads saved = adsRepository.save(ad);
        fillFiles(saved);
        notifyProposer(saved, "К сожалению, предложение рекламы отклонено.");
        return saved;
    }

    /** Сколько звёзд нужно заплатить за предложение. */
    public int priceOf(Ads ad) {
        return (Objects.isNull(ad.getDays()) ? 1 : ad.getDays()) * AdStatus.STARS_PER_DAY;
    }

    /** Создаёт ссылку на оплату звёздами для одобренного предложения. */
    public String createInvoiceLink(Long id, String userId) {
        Ads ad = getUserProposal(id, userId);
        if (!AdStatus.APPROVED.equals(ad.getStatus())) {
            throw new IllegalStateException("Предложение ещё не одобрено или уже оплачено");
        }
        return messageExecutor.createInvoiceLink(
                "Реклама, " + ad.getDays() + " дн.",
                "Показ рекламного поста в чате " + ad.getDays() + " дн.",
                PAYLOAD_PREFIX + ad.getId(),
                priceOf(ad));
    }

    /** Проверка перед списанием звёзд (pre_checkout_query). */
    public boolean canPay(String payload) {
        return parseProposal(payload)
                .map(ad -> AdStatus.APPROVED.equals(ad.getStatus()))
                .orElse(false);
    }

    /**
     * Оплата получена: пост становится обычной активной рекламой и публикуется
     * на оплаченное количество дней.
     */
    public void handleSuccessfulPayment(String payload, Integer amount, String chargeId) {
        Optional<Ads> found = parseProposal(payload);
        if (found.isEmpty()) {
            log.warn("Оплата по неизвестному счёту: {}", payload);
            return;
        }
        Ads ad = found.get();
        if (AdStatus.ACTIVE.equals(ad.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ad.setStatus(AdStatus.ACTIVE);
        ad.setStarsPaid(amount);
        ad.setPaymentChargeId(chargeId);
        ad.setDateStart(now);
        ad.setDateEnd(now.plusDays(Objects.isNull(ad.getDays()) ? 1 : ad.getDays()));
        adsRepository.save(ad);

        deleteLastMessages(ad);
        publish(ad);
        notifyProposer(ad, "Оплата получена! Реклама опубликована на " + ad.getDays() + " дн.");
    }

    private Optional<Ads> parseProposal(String payload) {
        if (Objects.isNull(payload) || !payload.startsWith(PAYLOAD_PREFIX)) {
            return Optional.empty();
        }
        try {
            return adsRepository.findById(Long.valueOf(payload.substring(PAYLOAD_PREFIX.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void notifySuperAdmin(Ads ad) {
        sendKeyboardMessage(String.valueOf(AccessConstants.SUPER_ADMIN_ID),
                "Модерация рекламы",
                "Новое предложение рекламы на " + ad.getDays() + " дн. от " + ad.getUserId(),
                APP_BASE_URL + "/app/ads");
    }

    private void notifyProposer(Ads ad, String text) {
        if (Objects.isNull(ad.getUserId())) {
            return;
        }
        sendKeyboardMessage(ad.getUserId(), "Открыть", text, APP_BASE_URL + "/app/ad-proposal");
    }

    private void sendKeyboardMessage(String chatId, String buttonText, String text, String url) {
        try {
            messageExecutor.sendMessage(KeyboardUtils.getKeyboardMessage(buttonText, text, url, chatId));
        } catch (Exception e) {
            log.error("Не удалось отправить уведомление в чат {}: {}", chatId, e.getMessage());
        }
    }

    // --- вложения -----------------------------------------------------------

    /**
     * Прикрепляет файлы к посту. Суммарно с уже прикреплёнными их должно быть не больше {@link #MAX_FILES}.
     */
    public void uploadFiles(Long adsId, List<MultipartFile> files) {
        getAdById(adsId);
        List<MultipartFile> actual = Objects.isNull(files) ? List.of() : files.stream()
                .filter(file -> Objects.nonNull(file) && !file.isEmpty())
                .collect(Collectors.toList());
        if (actual.isEmpty()) {
            return;
        }

        long existing = adsFileRepository.countByAdsId(adsId);
        if (existing + actual.size() > MAX_FILES) {
            throw new IllegalArgumentException("К посту можно прикрепить не больше " + MAX_FILES + " файлов");
        }

        Path dir = adsDir(adsId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать каталог для вложений", e);
        }

        for (MultipartFile file : actual) {
            String originalName = sanitize(Objects.isNull(file.getOriginalFilename())
                    ? "file"
                    : file.getOriginalFilename());
            String storedName = System.currentTimeMillis() + "_" + originalName;
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("Некорректное имя файла");
            }
            try {
                file.transferTo(target.toFile());
            } catch (IOException e) {
                throw new RuntimeException("Не удалось сохранить файл " + originalName, e);
            }

            AdsFile adsFile = new AdsFile();
            adsFile.setAdsId(adsId);
            adsFile.setOriginalName(originalName);
            adsFile.setStoredName(storedName);
            adsFile.setContentType(file.getContentType());
            adsFile.setFileSize(file.getSize());
            adsFile.setCreatedAt(LocalDateTime.now());
            adsFileRepository.save(adsFile);
        }
    }

    public void deleteFile(Long adsId, Long fileId) {
        AdsFile adsFile = adsFileRepository.findByIdAndAdsId(fileId, adsId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
        adsFileRepository.delete(adsFile);
        Path target = adsDir(adsId).resolve(adsFile.getStoredName()).normalize();
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.error("Не удалось удалить файл {}: {}", target, e.getMessage());
        }
    }

    public File loadFile(Long fileId) {
        return adsFileRepository.findById(fileId)
                .map(this::resolveFile)
                .orElse(null);
    }

    private List<File> findFiles(Long adsId) {
        return adsFileRepository.findByAdsIdOrderByIdAsc(adsId).stream()
                .map(this::resolveFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private File resolveFile(AdsFile adsFile) {
        Path path = adsDir(adsFile.getAdsId()).resolve(adsFile.getStoredName()).normalize();
        File file = path.toFile();
        return file.exists() && file.isFile() ? file : null;
    }

    private void fillFiles(Ads ad) {
        if (Objects.isNull(ad) || Objects.isNull(ad.getId())) {
            return;
        }
        List<AdsFileView> views = adsFileRepository.findByAdsIdOrderByIdAsc(ad.getId()).stream()
                .map(file -> new AdsFileView(file.getId(), file.getOriginalName(), "/api/ads/files/" + file.getId()))
                .collect(Collectors.toList());
        ad.setFiles(views);
    }

    private Path adsDir(Long adsId) {
        return Paths.get(uploadDir, ADS_DIR, String.valueOf(adsId)).normalize();
    }

    private void deleteFilesFromDisk(Long adsId) {
        Path dir = adsDir(adsId);
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.error("Не удалось удалить вложения рекламного поста {}: {}", adsId, e.getMessage());
        }
    }

    private String sanitize(String name) {
        String cleaned = name.replace('\\', '_').replace('/', '_').trim();
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.trim().isEmpty();
    }
}
