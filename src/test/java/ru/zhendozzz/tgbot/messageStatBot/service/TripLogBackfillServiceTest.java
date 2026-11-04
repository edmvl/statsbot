package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripLogBackfillServiceTest {

    private final LogRepo logRepo = mock(LogRepo.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final TripLogBackfillService service = new TripLogBackfillService(
            logRepo, settingsService, new TripMessageParser());

    @Test
    void parsesOldMessagesInBatches() {
        Log old = new Log();
        old.setChatId("-100");
        old.setDateTime(LocalDateTime.of(2026, 11, 4, 9, 0));
        old.setText("Сегодня в 18:00 из Чебоксар в Урмары");
        old.setTripParsed(false);

        when(settingsService.findTripChannelId()).thenReturn("-100");
        when(logRepo.findTop500ByChatIdAndTripParsedFalseOrderByIdAsc("-100"))
                .thenReturn(List.of(old))
                .thenReturn(List.of());

        service.backfill();

        verify(logRepo).saveAll(anyList());
        org.assertj.core.api.Assertions.assertThat(old.getTripFrom()).isEqualTo("Чебоксары");
        org.assertj.core.api.Assertions.assertThat(old.getTripTo()).isEqualTo("Урмары");
        org.assertj.core.api.Assertions.assertThat(old.getTripParsed()).isTrue();
    }

    @Test
    void skipsBackfillWhenTripChatIsNotConfigured() {
        when(settingsService.findTripChannelId()).thenReturn(null);

        service.backfill();

        verify(logRepo, never()).findTop500ByChatIdAndTripParsedFalseOrderByIdAsc("-100");
        verify(logRepo, never()).saveAll(anyList());
    }
}
