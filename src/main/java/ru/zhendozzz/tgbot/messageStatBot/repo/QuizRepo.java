package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.dto.QuizChatDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuizDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepo extends JpaRepository<Quiz, Long> {

    @Query(value = "select q.id as id, q.question as question, q.ask as ask\n" +
            "from quiz q left join quiz_chat qc on q.id=qc.id where qc.chat_id is null or qc.chat_id <> ?1", nativeQuery = true)
    List<QuizDto> findNotAskedQuizzesByChatId(String chatId);

    @Query(value = "select qc.id, qc.chat_id as chatId, qc.message_id as messageId, q.ask as ask \n" +
            "from quiz_chat qc left join quiz q  on q.id=qc.question_id where qc.chat_id = ?1 and qc.winner_id is null", nativeQuery = true)
    QuizChatDto findLastQuizForChat(String chatId);

}
