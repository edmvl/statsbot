package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripMessageInfo;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.util.List;
import java.util.Objects;

/**
 * Одноразовый фоновый разбор старых сообщений канала поездок.
 *
 * <p>Обрабатывает только строки с {@code trip_parsed = false}, батчами по 500,
 * поэтому не блокирует старт приложения и не создаёт пиковую нагрузку. После
 * обработки ставит {@code trip_parsed = true}, чтобы не разбирать сообщение снова.</p>
 */
@Slf4j
@Component
public class TripLogBackfillService implements ApplicationRunner {

    private static final int BATCH_SIZE_HINT = 500;
    private static final long PAUSE_MS = 100;

    private final LogRepo logRepo;
    private final SettingsService settingsService;
    private final TripMessageParser parser;

    public TripLogBackfillService(LogRepo logRepo, SettingsService settingsService, TripMessageParser parser) {
        this.logRepo = logRepo;
        this.settingsService = settingsService;
        this.parser = parser;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(this::backfill, "trip-log-backfill");
        thread.setDaemon(true);
        thread.start();
    }

    void backfill() {
        String chatId = settingsService.findTripChannelId();
        if (chatId == null) {
            log.info("Канал поездок не настроен — фоновый разбор пропущен");
            return;
        }
        int total = 0;
        while (true) {
            List<Log> batch;
            try {
                batch = logRepo.findTop500ByChatIdAndTripParsedFalseOrderByIdAsc(chatId);
            } catch (Exception e) {
                log.error("Не удалось получить пачку старых сообщений поездок: {}", e.getMessage());
                return;
            }
            if (batch.isEmpty()) {
                break;
            }
            for (Log row : batch) {
                TripMessageInfo info = parser.parse(row.getText(), row.getDateTime());
                if (Objects.nonNull(info.from())) {
                    row.setTripFrom(info.from());
                }
                if (Objects.nonNull(info.to())) {
                    row.setTripTo(info.to());
                }
                if (Objects.nonNull(info.tripTime())) {
                    row.setTripTime(info.tripTime());
                }
                row.setTripParsed(true);
            }
            try {
                logRepo.saveAll(batch);
            } catch (Exception e) {
                log.error("Не удалось сохранить разобранные сообщения поездок: {}", e.getMessage());
                return;
            }
            total += batch.size();
            log.info("Разобрано старых сообщений поездок: {}", total);
            if (batch.size() < BATCH_SIZE_HINT) {
                break;
            }
            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("Фоновый разбор сообщений поездок завершён, всего: {}", total);
    }
}
