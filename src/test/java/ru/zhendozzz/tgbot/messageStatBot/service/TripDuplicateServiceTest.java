package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripDuplicateServiceTest {

    private final LogRepo logRepo = mock(LogRepo.class);
    private final TripDuplicateService service = new TripDuplicateService(logRepo);

    @Test
    void keepsOnlyLatestMessageOfCloseTrips() {
        Log first = trip(1L, LocalDateTime.of(2026, 11, 5, 10, 0));
        Log second = trip(2L, LocalDateTime.of(2026, 11, 5, 10, 10));
        Log third = trip(3L, LocalDateTime.of(2026, 11, 5, 10, 20));
        when(logRepo.findTripCandidatesForDay(
                eq("-100"), eq("42"), eq("Чебоксары"), eq("Урмары"),
                any(), any())).thenReturn(List.of(first, second, third));

        service.regroup(third);

        assertThat(first.getTripDuplicateOf()).isEqualTo(3L);
        assertThat(second.getTripDuplicateOf()).isEqualTo(3L);
        assertThat(third.getTripDuplicateOf()).isNull();
        verify(logRepo).saveAll(anyList());
    }

    @Test
    void doesNotMergeTripsFurtherThanFifteenMinutesApart() {
        Log first = trip(1L, LocalDateTime.of(2026, 11, 5, 10, 0));
        Log second = trip(2L, LocalDateTime.of(2026, 11, 5, 10, 20));
        when(logRepo.findTripCandidatesForDay(
                eq("-100"), eq("42"), eq("Чебоксары"), eq("Урмары"),
                any(), any())).thenReturn(List.of(first, second));

        service.regroup(second);

        assertThat(first.getTripDuplicateOf()).isNull();
        assertThat(second.getTripDuplicateOf()).isNull();
        verify(logRepo, org.mockito.Mockito.never()).saveAll(anyList());
    }

    @Test
    void regroupAllProcessesOldTrips() {
        Log first = trip(1L, LocalDateTime.of(2026, 11, 5, 10, 0));
        Log second = trip(2L, LocalDateTime.of(2026, 11, 5, 10, 12));
        when(logRepo.findDistinctTripUserIds("-100")).thenReturn(List.of("42"));
        when(logRepo.findTripRowsForUser("-100", "42")).thenReturn(List.of(first, second));

        service.regroupAll("-100");

        assertThat(first.getTripDuplicateOf()).isEqualTo(2L);
        assertThat(second.getTripDuplicateOf()).isNull();
        verify(logRepo).saveAll(anyList());
    }

    private Log trip(Long id, LocalDateTime time) {
        Log log = new Log();
        log.setId(id);
        log.setChatId("-100");
        log.setUserId("42");
        log.setTripFrom("Чебоксары");
        log.setTripTo("Урмары");
        log.setTripTime(time);
        return log;
    }
}
