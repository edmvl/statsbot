package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestCheckpoint;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestCheckpointRepo extends JpaRepository<QuestCheckpoint, Long> {

    List<QuestCheckpoint> findByQuestIdOrderBySortOrderAscIdAsc(Long questId);

    Optional<QuestCheckpoint> findByIdAndQuestId(Long id, Long questId);

    long countByQuestId(Long questId);
}
