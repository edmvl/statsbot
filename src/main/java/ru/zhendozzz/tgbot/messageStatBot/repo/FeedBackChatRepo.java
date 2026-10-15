package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBackChat;

import java.util.List;

@Repository
public interface FeedBackChatRepo extends JpaRepository<FeedBackChat, Long> {
    List<FeedBackChat> findByFeedbackId(Long chatId);
}
