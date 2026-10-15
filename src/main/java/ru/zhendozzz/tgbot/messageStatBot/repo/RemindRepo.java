package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.Remind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RemindRepo extends JpaRepository<Remind, Long> {
    List<Remind> findAllByActiveIsTrueAndChatId(String chatId);
    List<Remind> getRemindByChatIdAndReplyMessageId(String chatId, String replyMessageId);
}
