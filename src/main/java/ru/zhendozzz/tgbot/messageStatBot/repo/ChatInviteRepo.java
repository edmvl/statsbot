package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatInviteViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatInvite;

import java.util.List;

@Repository
public interface ChatInviteRepo extends JpaRepository<ChatInvite, Long> {

    /**
     * Запоминает приглашение; при повторном добавлении той же пары обновляет дату,
     * чтобы не создавать дубли.
     */
    @Modifying
    @Transactional
    @Query(value = "insert into chat_invite (chat_id, inviter_id, invitee_id, invited_at) "
            + "values (:chatId, :inviterId, :inviteeId, now()) "
            + "on conflict (chat_id, inviter_id, invitee_id) "
            + "do update set invited_at = excluded.invited_at", nativeQuery = true)
    void upsert(@Param("chatId") String chatId,
                @Param("inviterId") String inviterId,
                @Param("inviteeId") String inviteeId);

    /** Кто добавлял пользователя в этот чат (свежие приглашения первыми). */
    @Query(value = "select u.id as userId, "
            + "trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) as name, "
            + "u.user_name as userName, u.avatar_path as avatarPath, i.invited_at as invitedAt "
            + "from chat_invite i left join users u on u.id = i.inviter_id "
            + "where i.chat_id = :chatId and i.invitee_id = :inviteeId "
            + "order by i.invited_at desc, i.id desc", nativeQuery = true)
    List<ChatInviteViewDto> findInviters(@Param("chatId") String chatId,
                                         @Param("inviteeId") String inviteeId);

    /** Кого пользователь добавлял в этот чат (свежие приглашения первыми). */
    @Query(value = "select u.id as userId, "
            + "trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) as name, "
            + "u.user_name as userName, u.avatar_path as avatarPath, i.invited_at as invitedAt "
            + "from chat_invite i left join users u on u.id = i.invitee_id "
            + "where i.chat_id = :chatId and i.inviter_id = :inviterId "
            + "order by i.invited_at desc, i.id desc", nativeQuery = true)
    List<ChatInviteViewDto> findInvitees(@Param("chatId") String chatId,
                                         @Param("inviterId") String inviterId);
}
