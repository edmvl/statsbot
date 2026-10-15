package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.FeedBack;

import java.util.List;

@Repository
public interface FeedBackRepo extends JpaRepository<FeedBack, Long> {
    @Query(value = "select user_id from settings where chat_id=(select chat_id from chat_settings where setting_name='trip_channel' and setting_value='true' order by id limit 1) and is_admin=true;", nativeQuery = true)
    List<String> getTripGroupAdmins();
}
