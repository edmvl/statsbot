package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestParticipantView;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestParticipant;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestParticipantRepo extends JpaRepository<QuestParticipant, Long> {

    Optional<QuestParticipant> findByQuestIdAndUserId(Long questId, String userId);

    long countByQuestId(Long questId);

    long countByQuestIdAndFinishedAtNotNull(Long questId);

    /** Участники квеста с именем из users и числом пройденных чекпоинтов. */
    @Query(value = "select p.id as participantId, p.user_id as userId, "
            + "u.first_name as firstName, u.last_name as lastName, u.user_name as userName, "
            + "u.avatar_path as avatarPath, p.started_at as startedAt, p.finished_at as finishedAt, "
            + "(select count(*) from quest_pass pp where pp.participant_id = p.id) as passed "
            + "from quest_participant p left join users u on u.id = p.user_id "
            + "where p.quest_id = :questId "
            + "order by p.finished_at nulls last, p.started_at asc",
            nativeQuery = true)
    List<QuestParticipantView> findParticipantViews(@Param("questId") Long questId);
}
