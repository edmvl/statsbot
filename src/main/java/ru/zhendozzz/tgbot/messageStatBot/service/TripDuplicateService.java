package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Объединяет повторные публикации одной поездки.
 *
 * <p>Повтором считаются сообщения одного водителя, с одинаковыми точками
 * отправления/прибытия и временем в пределах 15 минут. Внутри группы основным
 * остаётся самое свежее сообщение; остальные получают {@code trip_duplicate_of}.</p>
 */
@Slf4j
@Service
public class TripDuplicateService {

    static final Duration MAX_TIME_GAP = Duration.ofMinutes(15);
    private static final int SAVE_BATCH = 500;

    private final LogRepo logRepo;

    public TripDuplicateService(LogRepo logRepo) {
        this.logRepo = logRepo;
    }

    /** Пересчитывает группу для сохранённого/отредактированного сообщения. */
    public void regroup(Log changed) {
        if (Objects.isNull(changed) || Objects.isNull(changed.getId())
                || Objects.isNull(changed.getTripTime())
                || (Objects.isNull(changed.getTripFrom()) && Objects.isNull(changed.getTripTo()))) {
            if (Objects.nonNull(changed) && Objects.nonNull(changed.getTripDuplicateOf())) {
                changed.setTripDuplicateOf(null);
                logRepo.save(changed);
            }
            return;
        }

        LocalDate day = changed.getTripTime().toLocalDate();
        List<Log> candidates = logRepo.findTripCandidatesForDay(
                changed.getChatId(),
                changed.getUserId(),
                changed.getTripFrom(),
                changed.getTripTo(),
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay());

        List<Log> cluster = findCluster(candidates, changed.getId());
        if (cluster.isEmpty()) {
            return;
        }
        persistCluster(cluster);
    }

    /** Пересчитывает все поездки чата (для старых сообщений). */
    public void regroupAll(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        for (String userId : logRepo.findDistinctTripUserIds(chatId)) {
            List<Log> rows = logRepo.findTripRowsForUser(chatId, userId);
            List<Log> changed = new ArrayList<>();
            String currentFrom = null;
            String currentTo = null;
            LocalDate currentDay = null;
            LocalDateTime previous = null;
            List<Log> cluster = new ArrayList<>();

            for (Log row : rows) {
                String rowFrom = normalize(row.getTripFrom());
                String rowTo = normalize(row.getTripTo());
                LocalDate rowDay = row.getTripTime().toLocalDate();
                boolean sameKey = Objects.equals(rowFrom, currentFrom)
                        && Objects.equals(rowTo, currentTo)
                        && Objects.equals(rowDay, currentDay);
                boolean closeEnough = Objects.nonNull(previous)
                        && Duration.between(previous, row.getTripTime()).compareTo(MAX_TIME_GAP) <= 0;

                if (!sameKey || !closeEnough) {
                    collectChanged(cluster, changed);
                    cluster = new ArrayList<>();
                    currentFrom = rowFrom;
                    currentTo = rowTo;
                    currentDay = rowDay;
                }
                cluster.add(row);
                previous = row.getTripTime();
            }
            collectChanged(cluster, changed);
            saveInBatches(changed);
        }
        log.info("Группировка поездок завершена для чата {}", chatId);
    }

    private List<Log> findCluster(List<Log> rows, Long id) {
        List<Log> cluster = new ArrayList<>();
        LocalDateTime previous = null;
        for (Log row : rows) {
            boolean closeEnough = Objects.nonNull(previous)
                    && Duration.between(previous, row.getTripTime()).compareTo(MAX_TIME_GAP) <= 0;
            if (Objects.nonNull(previous) && !closeEnough) {
                if (containsId(cluster, id)) {
                    return cluster;
                }
                cluster = new ArrayList<>();
            }
            cluster.add(row);
            previous = row.getTripTime();
        }
        return containsId(cluster, id) ? cluster : List.of();
    }

    private boolean containsId(List<Log> cluster, Long id) {
        return cluster.stream().anyMatch(row -> Objects.equals(row.getId(), id));
    }

    private void collectChanged(List<Log> cluster, List<Log> changed) {
        if (cluster.isEmpty()) {
            return;
        }
        Log primary = cluster.stream()
                .max(Comparator.comparing(Log::getId))
                .orElse(cluster.get(cluster.size() - 1));
        for (Log row : cluster) {
            Long desired = Objects.equals(row.getId(), primary.getId()) ? null : primary.getId();
            if (!Objects.equals(row.getTripDuplicateOf(), desired)) {
                row.setTripDuplicateOf(desired);
                changed.add(row);
            }
        }
    }

    private void persistCluster(List<Log> cluster) {
        List<Log> changed = new ArrayList<>();
        collectChanged(cluster, changed);
        saveInBatches(changed);
    }

    private void saveInBatches(List<Log> rows) {
        for (int from = 0; from < rows.size(); from += SAVE_BATCH) {
            int to = Math.min(rows.size(), from + SAVE_BATCH);
            logRepo.saveAll(rows.subList(from, to));
        }
    }

    private String normalize(String value) {
        return Objects.isNull(value) ? "" : value.trim();
    }
}
