package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.zhendozzz.tgbot.messageStatBot.dto.InterestedViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Interested;

import java.util.List;

@Repository
public interface InterestedRepo extends JpaRepository<Interested, Long> {
    List<Interested> getInterestedByInterestIdAndChatId(String interestId, String chatId);

    List<Interested> findByUserIdAndChatId(String userId, String chatId);

    List<Interested> findByUserIdAndChatIdAndInterestId(String userId, String chatId, String interestId);

    @Modifying
    @Transactional
    void deleteByUserIdAndChatIdAndInterestId(String userId, String chatId, String interestId);

    @Modifying
    @Transactional
    void deleteByUserIdAndInterestId(String userId, String interestId);

    /**
     * Все подписки с данными подписчика и автора (из {@code users}).
     */
    @Query(value = "select i.user_id as userId, i.interest_id as interestId, i.chat_id as chatId, "
            + "su.first_name as userFirstName, su.last_name as userLastName, su.user_name as userName, "
            + "su.avatar_path as userAvatarPath, "
            + "au.first_name as interestFirstName, au.last_name as interestLastName, au.user_name as interestUserName, "
            + "au.avatar_path as interestAvatarPath "
            + "from interested i "
            + "left join users su on su.id = i.user_id "
            + "left join users au on au.id = i.interest_id "
            + "order by su.first_name nulls last, au.first_name nulls last", nativeQuery = true)
    List<InterestedViewDto> findAllWithUsers();

    /**
     * Подписки конкретного пользователя (на кого он подписан) с данными авторов.
     */
    @Query(value = "select i.user_id as userId, i.interest_id as interestId, i.chat_id as chatId, "
            + "su.first_name as userFirstName, su.last_name as userLastName, su.user_name as userName, "
            + "su.avatar_path as userAvatarPath, "
            + "au.first_name as interestFirstName, au.last_name as interestLastName, au.user_name as interestUserName, "
            + "au.avatar_path as interestAvatarPath "
            + "from interested i "
            + "left join users su on su.id = i.user_id "
            + "left join users au on au.id = i.interest_id "
            + "where i.user_id = :userId "
            + "order by au.first_name nulls last, au.last_name nulls last", nativeQuery = true)
    List<InterestedViewDto> findAllWithUsersByUserId(@Param("userId") String userId);
}
