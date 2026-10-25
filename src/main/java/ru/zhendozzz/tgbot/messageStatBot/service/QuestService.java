package ru.zhendozzz.tgbot.messageStatBot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestParticipantView;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestProgress;
import ru.zhendozzz.tgbot.messageStatBot.entity.Quest;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestCheckpoint;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestParticipant;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestPass;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestCheckpointRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestParticipantRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestPassRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestRepo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Квесты: чекпоинты с координатами и вопросами, прохождение участников.
 *
 * <p>Ответ принимается только рядом с точкой: расстояние считается по координатам,
 * которые прислал клиент (геолокация браузера). Подделать координаты технически можно —
 * для квестов это осознанно допустимо.</p>
 */
@Service
public class QuestService {

    /** Радиус по умолчанию, если админ не задал. */
    public static final int DEFAULT_RADIUS_M = 50;
    /** Верхняя граница радиуса появления. */
    public static final int MAX_RADIUS_M = 5000;

    private static final double EARTH_RADIUS_M = 6_371_000d;
    private static final String MEDIA_DIR_PREFIX = "quest-";

    private final QuestRepo questRepo;
    private final QuestCheckpointRepo checkpointRepo;
    private final QuestParticipantRepo participantRepo;
    private final QuestPassRepo passRepo;
    private final FileLoaderService fileLoaderService;

    public QuestService(
            QuestRepo questRepo,
            QuestCheckpointRepo checkpointRepo,
            QuestParticipantRepo participantRepo,
            QuestPassRepo passRepo,
            FileLoaderService fileLoaderService
    ) {
        this.questRepo = questRepo;
        this.checkpointRepo = checkpointRepo;
        this.participantRepo = participantRepo;
        this.passRepo = passRepo;
        this.fileLoaderService = fileLoaderService;
    }

    /** Результат попытки ответить на вопрос чекпоинта. */
    public enum AnswerResult {
        /** Ответ принят. */
        OK,
        /** Неверный ответ. */
        WRONG,
        /** Слишком далеко от точки (или не переданы координаты). */
        TOO_FAR,
        /** Этот чекпоинт уже пройден. */
        ALREADY,
        /** Пользователь ещё не нажал «Участвовать». */
        NOT_JOINED,
        /** Квест выключен или не найден. */
        NOT_ACTIVE
    }

    public List<Quest> findAll() {
        return questRepo.findAllByOrderByIdDesc();
    }

    /** Квесты, доступные участникам. */
    public List<Quest> findActive() {
        return questRepo.findByActiveTrueOrderByIdDesc();
    }

    public Quest findByIdOrNull(Long id) {
        return Objects.isNull(id) ? null : questRepo.findById(id).orElse(null);
    }

    public List<QuestCheckpoint> findCheckpoints(Long questId) {
        return checkpointRepo.findByQuestIdOrderBySortOrderAscIdAsc(questId);
    }

    public long countCheckpoints(Long questId) {
        return checkpointRepo.countByQuestId(questId);
    }

    public Quest create(String title, String description, String adminId) {
        if (isBlank(title)) {
            throw new IllegalArgumentException("Укажите название квеста");
        }
        Quest quest = new Quest();
        quest.setTitle(title.trim());
        quest.setDescription(trimToEmpty(description));
        quest.setActive(true);
        quest.setCreatedBy(adminId);
        quest.setCreatedAt(LocalDateTime.now());
        return questRepo.save(quest);
    }

    public void update(Long questId, String title, String description, boolean active) {
        Quest quest = requireQuest(questId);
        if (isBlank(title)) {
            throw new IllegalArgumentException("Укажите название квеста");
        }
        quest.setTitle(title.trim());
        quest.setDescription(trimToEmpty(description));
        quest.setActive(active);
        questRepo.save(quest);
    }

    /**
     * Добавляет чекпоинт. Радиус — «радиус появления»: внутри него вопрос открывается
     * и ответ принимается. К чекпоинту можно приложить картинку или звук.
     */
    public QuestCheckpoint addCheckpoint(
            Long questId,
            String title,
            String question,
            String answer,
            String lat,
            String lon,
            String radius,
            MultipartFile media
    ) {
        requireQuest(questId);
        if (isBlank(question)) {
            throw new IllegalArgumentException("Укажите вопрос чекпоинта");
        }
        if (isBlank(answer)) {
            throw new IllegalArgumentException("Укажите правильный ответ");
        }
        Double latitude = parseCoordinate(lat);
        Double longitude = parseCoordinate(lon);
        if (Objects.isNull(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Широта должна быть от -90 до 90");
        }
        if (Objects.isNull(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Долгота должна быть от -180 до 180");
        }
        // Тип вложения проверяем до сохранения, чтобы не оставить полупустой чекпоинт.
        String mediaType = Objects.nonNull(media) && !media.isEmpty() ? resolveMediaType(media) : null;

        QuestCheckpoint checkpoint = new QuestCheckpoint();
        checkpoint.setQuestId(questId);
        checkpoint.setSortOrder((int) checkpointRepo.countByQuestId(questId) + 1);
        checkpoint.setTitle(isBlank(title) ? "Чекпоинт " + checkpoint.getSortOrder() : title.trim());
        checkpoint.setQuestion(question.trim());
        checkpoint.setAnswer(answer.trim());
        checkpoint.setLat(latitude);
        checkpoint.setLon(longitude);
        checkpoint.setRadiusM(normalizeRadius(radius));
        checkpoint = checkpointRepo.save(checkpoint);

        if (Objects.nonNull(mediaType)) {
            String fileName = fileLoaderService.saveSingleFile(media, mediaDir(checkpoint.getId()));
            checkpoint.setMediaType(mediaType);
            checkpoint.setMediaFile(fileName);
            checkpoint = checkpointRepo.save(checkpoint);
        }
        return checkpoint;
    }

    public void deleteCheckpoint(Long questId, Long checkpointId) {
        QuestCheckpoint checkpoint = checkpointRepo.findByIdAndQuestId(checkpointId, questId)
                .orElseThrow(() -> new IllegalArgumentException("Чекпоинт не найден"));
        fileLoaderService.clearFiles(mediaDir(checkpoint.getId()));
        checkpointRepo.delete(checkpoint);
    }

    /** Каталог вложений чекпоинта — он же ключ для {@code /images?id=...}. */
    public static String mediaDir(Long checkpointId) {
        return MEDIA_DIR_PREFIX + checkpointId;
    }

    public QuestParticipant join(Long questId, String userId) {
        Quest quest = requireQuest(questId);
        if (!Boolean.TRUE.equals(quest.getActive())) {
            throw new IllegalStateException("Квест недоступен");
        }
        return participantRepo.findByQuestIdAndUserId(questId, userId).orElseGet(() -> {
            QuestParticipant participant = new QuestParticipant();
            participant.setQuestId(questId);
            participant.setUserId(userId);
            participant.setStartedAt(LocalDateTime.now());
            return participantRepo.save(participant);
        });
    }

    /**
     * Проверяет ответ: рядом ли участник (радиус появления), верный ли ответ,
     * не пройден ли чекпоинт раньше. На последнем чекпоинте квест закрывается.
     */
    public AnswerResult answer(Long questId, String userId, Long checkpointId,
                               String answer, Double lat, Double lon) {
        Quest quest = findByIdOrNull(questId);
        if (Objects.isNull(quest) || !Boolean.TRUE.equals(quest.getActive())) {
            return AnswerResult.NOT_ACTIVE;
        }
        QuestParticipant participant = participantRepo.findByQuestIdAndUserId(questId, userId).orElse(null);
        if (Objects.isNull(participant)) {
            return AnswerResult.NOT_JOINED;
        }
        Optional<QuestCheckpoint> found = checkpointRepo.findByIdAndQuestId(checkpointId, questId);
        if (found.isEmpty()) {
            return AnswerResult.NOT_ACTIVE;
        }
        QuestCheckpoint checkpoint = found.get();
        if (passRepo.findByParticipantIdAndCheckpointId(participant.getId(), checkpoint.getId()).isPresent()) {
            return AnswerResult.ALREADY;
        }
        if (Objects.isNull(lat) || Objects.isNull(lon)) {
            return AnswerResult.TOO_FAR;
        }
        double distance = distanceMeters(lat, lon, checkpoint.getLat(), checkpoint.getLon());
        if (distance > normalizeRadius(Objects.toString(checkpoint.getRadiusM(), null))) {
            return AnswerResult.TOO_FAR;
        }
        if (!normalizeAnswer(answer).equals(normalizeAnswer(checkpoint.getAnswer()))) {
            return AnswerResult.WRONG;
        }

        QuestPass pass = new QuestPass();
        pass.setParticipantId(participant.getId());
        pass.setCheckpointId(checkpoint.getId());
        pass.setAnswer(trimToEmpty(answer));
        pass.setPassedAt(LocalDateTime.now());
        passRepo.save(pass);

        long passed = passRepo.countByParticipantId(participant.getId());
        long total = checkpointRepo.countByQuestId(questId);
        if (passed >= total && Objects.isNull(participant.getFinishedAt())) {
            participant.setFinishedAt(LocalDateTime.now());
            participantRepo.save(participant);
        }
        return AnswerResult.OK;
    }

    public QuestProgress progress(Long questId, String userId) {
        QuestParticipant participant = Objects.isNull(userId)
                ? null
                : participantRepo.findByQuestIdAndUserId(questId, userId).orElse(null);
        Set<Long> passedIds = Objects.isNull(participant)
                ? Set.of()
                : passRepo.findByParticipantId(participant.getId()).stream()
                        .map(QuestPass::getCheckpointId)
                        .collect(Collectors.toSet());
        int total = (int) checkpointRepo.countByQuestId(questId);
        return new QuestProgress(
                Objects.nonNull(participant),
                Objects.nonNull(participant) && Objects.nonNull(participant.getFinishedAt()),
                passedIds,
                passedIds.size(),
                total);
    }

    public List<QuestParticipantView> participantViews(Long questId) {
        return participantRepo.findParticipantViews(questId);
    }

    /** Расстояние между координатами в метрах (формула гаверсинуса). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Quest requireQuest(Long questId) {
        return questRepo.findById(questId)
                .orElseThrow(() -> new IllegalArgumentException("Квест не найден"));
    }

    private int normalizeRadius(String radius) {
        if (isBlank(radius)) {
            return DEFAULT_RADIUS_M;
        }
        try {
            int value = (int) Math.round(Double.parseDouble(radius.trim().replace(',', '.')));
            if (value <= 0) {
                return DEFAULT_RADIUS_M;
            }
            return Math.min(value, MAX_RADIUS_M);
        } catch (NumberFormatException e) {
            return DEFAULT_RADIUS_M;
        }
    }

    private Double parseCoordinate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Double.valueOf(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ответ сравниваем без регистра, лишних пробелов и с «ё» → «е». */
    static String normalizeAnswer(String value) {
        return trimToEmpty(value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ");
    }

    private String resolveMediaType(MultipartFile file) {
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/")) {
            return "image";
        }
        if (contentType.startsWith("audio/")) {
            return "audio";
        }
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (name.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
            return "image";
        }
        if (name.matches(".*\\.(mp3|ogg|wav|m4a|oga|opus)$")) {
            return "audio";
        }
        throw new IllegalArgumentException("К вопросу можно прикрепить картинку или звук");
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.trim().isEmpty();
    }

    private static String trimToEmpty(String value) {
        return Objects.isNull(value) ? "" : value.trim();
    }
}
