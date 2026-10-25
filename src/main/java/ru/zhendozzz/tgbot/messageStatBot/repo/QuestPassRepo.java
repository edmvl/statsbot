package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestPass;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestPassRepo extends JpaRepository<QuestPass, Long> {

    List<QuestPass> findByParticipantId(Long participantId);

    Optional<QuestPass> findByParticipantIdAndCheckpointId(Long participantId, Long checkpointId);

    long countByParticipantId(Long participantId);
}
