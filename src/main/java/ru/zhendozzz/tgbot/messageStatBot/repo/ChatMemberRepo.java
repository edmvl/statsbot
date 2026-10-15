package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatMember;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMemberRepo extends JpaRepository<ChatMember, Long> {
    List<ChatMember> findByUserIdAndChatId(String userId, String chatId);

    List<ChatMember> getChatMemberByChatId(String chatId);

    List<ChatMember> getChatMemberByChatIdAndLastMessageIsBefore(String chatId, LocalDateTime dateTime);
}
