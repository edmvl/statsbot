package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.UserNameHistory;

import java.util.List;

@Repository
public interface UserNameHistoryRepo extends JpaRepository<UserNameHistory, Long> {
    List<UserNameHistory> findByUserIdOrderByChangedAtDesc(String userId);
}
