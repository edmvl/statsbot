package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ChallengeRepo extends JpaRepository<Challenge, Long> {
    Challenge findByChatIdAndDateTimeEndAfterAndFinished(String chatId, LocalDateTime dateTime, Boolean finished);
    Challenge findByChatIdAndDateTimeEndLessThanAndFinished(String chatId, LocalDateTime dateTime, Boolean finished);
}
