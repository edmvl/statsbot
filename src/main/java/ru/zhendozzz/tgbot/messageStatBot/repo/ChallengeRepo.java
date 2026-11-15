package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChallengeRepo extends JpaRepository<Challenge, Long> {
    Challenge findByChatIdAndDateTimeEndAfterAndFinished(String chatId, LocalDateTime dateTime, Boolean finished);
    Challenge findByChatIdAndDateTimeEndLessThanAndFinished(String chatId, LocalDateTime dateTime, Boolean finished);

    /** Чаты, где есть незавершённые челленджи — вместо {@code findAll()} каждую минуту. */
    @Query("select distinct c.chatId from Challenge c where c.finished = false")
    List<String> findDistinctChatIdsByFinishedFalse();
}
