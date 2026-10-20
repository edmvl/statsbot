package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.BannedUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface BannedUserRepo extends JpaRepository<BannedUser, Long> {

    List<BannedUser> findAllByOrderByBannedAtDesc();

    Optional<BannedUser> findFirstByUserIdOrderByBannedAtDesc(String userId);

    boolean existsByUserIdAndChatId(String userId, String chatId);

    boolean existsByFeedbackId(Long feedbackId);
}
