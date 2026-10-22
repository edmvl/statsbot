package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.LogEdit;

import java.util.Collection;
import java.util.List;

@Repository
public interface LogEditRepo extends JpaRepository<LogEdit, Long> {

    /** Предыдущие версии сообщений страницы, в порядке появления. */
    List<LogEdit> findByLogIdInOrderByIdAsc(Collection<Long> logIds);
}
