package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.Quest;

import java.util.List;

@Repository
public interface QuestRepo extends JpaRepository<Quest, Long> {

    List<Quest> findAllByOrderByIdDesc();

    List<Quest> findByActiveTrueOrderByIdDesc();
}
