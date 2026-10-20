package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.entity.Rating;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.RatingRepo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TripServiceTest {

    private final RatingRepo ratingRepo = mock(RatingRepo.class);
    private final TripService tripService =
            new TripService(mock(MessageExecutor.class), mock(LogRepo.class), ratingRepo);

    @Test
    void doesNotSaveReviewAboutYourself() {
        assertFalse(tripService.addRating("42", "42", "5", "Самому себе"));

        verify(ratingRepo, never()).save(any(Rating.class));
    }

    @Test
    void savesReviewAboutAnotherUser() {
        assertTrue(tripService.addRating("42", "43", "5", "Отличная поездка"));

        verify(ratingRepo).save(any(Rating.class));
    }
}
