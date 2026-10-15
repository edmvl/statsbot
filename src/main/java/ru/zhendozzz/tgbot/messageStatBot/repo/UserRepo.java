package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMemberViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.TgUser;

import java.util.List;

@Repository
public interface UserRepo extends JpaRepository<TgUser, String> {

    @Query(value = "select id as userId, "
            + "trim(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) as name, "
            + "user_name as userName, "
            + "avatar_path as avatarPath "
            + "from users where id in (:ids)", nativeQuery = true)
    List<ChatMemberViewDto> findMemberViewsByIds(@Param("ids") List<String> ids);
}
