package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMemberViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatMessageDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChatViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.ChattyDaysDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.StatsViewDto;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripViewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface LogRepo extends JpaRepository<Log, Long> {

    /**
     * Отображаемое имя пользователя (ФИО и ник) берётся из таблицы {@code users}.
     */
    @Query(value = "select concat_ws(' ', first_name, last_name) || COALESCE(' (' || user_name || ')', '') "
            + "from users where id = ?1", nativeQuery = true)
    List<String> findUserNameByUserId(String userId);

    @Query(value = "select distinct l.chat_id from log l where cast(l.chat_id as bigint)<0", nativeQuery = true)
    List<String> findDistinctChatId();

    @Query(value = "select TO_DATE(TO_CHAR(date_time, 'dd.mm.yyyy'), 'dd.mm.yyyy') as date, count(0) as sm from log l " +
            "where l.chat_id = ?1 group by date order by sm desc limit 10", nativeQuery = true)
    List<ChattyDaysDto> findTopChattyDays(String chatId);

    @Query(value = "select  chat_id as chatId, user_id as userId, count(0) as count from log l " +
            "where l.chat_id = ?1 and date_time between ?2 and ?3 " +
            "group by user_id, chat_id order by count desc limit 10", nativeQuery = true)
    List<StatsViewDto> findByChatIdAndDateBetweenOrderByCountDesc(String chatId, LocalDateTime startDate, LocalDateTime endDate);

    @Query(value = "select distinct l.user_id from log l where l.chat_id = ?1", nativeQuery = true)
    List<String> findDistinctUserIdByChatId(String chatId);

    /**
     * Участники чата (distinct user_id по логу) с именем из {@code users}.
     * Поддерживает серверный поиск по имени/нику/id и ограничение количества.
     */
    @Query(value = "select m.user_id as userId, "
            + "trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) as name, "
            + "u.user_name as userName, "
            + "u.avatar_path as avatarPath "
            + "from (select distinct user_id from log where chat_id = :chatId) m "
            + "left join users u on u.id = m.user_id "
            + "where (cast(:q as text) = '' "
            + "   or coalesce(u.first_name, '') ilike '%' || :q || '%' "
            + "   or coalesce(u.last_name, '') ilike '%' || :q || '%' "
            + "   or u.user_name ilike '%' || :q || '%' "
            + "   or m.user_id = :q) "
            + "order by name asc, m.user_id asc "
            + "limit :limit", nativeQuery = true)
    List<ChatMemberViewDto> findChatMembers(
            @Param("chatId") String chatId,
            @Param("q") String q,
            @Param("limit") int limit);

    /**
     * Сообщения чата с именем отправителя из {@code users}.
     */
    @Query(value = "select l.id as id, l.date_time as dateTime, l.user_id as userId, l.text as text, "
            + "u.first_name as firstName, u.last_name as lastName, u.user_name as userName "
            + "from log l left join users u on u.id = l.user_id "
            + "where l.chat_id = :chatId order by l.date_time desc",
            countQuery = "select count(*) from log where chat_id = :chatId",
            nativeQuery = true)
    Page<ChatMessageDto> getMessagesByChatId(@Param("chatId") String chatId, Pageable pageable);

    @Query(value = "select chat_name from log where chat_id = ?1 and chat_name is not null order by id desc limit 1", nativeQuery = true)
    String findChatNameByChatId(String chatId);

    @Query(value = "select chat_id as chatId, chat_name as chatName, "
            + "to_char(date_time, 'DD.MM.YYYY HH24:MI') as lastMessage "
            + "from (select distinct on (chat_id) chat_id, chat_name, date_time from log where chat_name is not null "
            + "order by chat_id, id desc) as t "
            + "order by date_time desc", nativeQuery = true)
    List<ChatViewDto> getAllChats();

    @Query(value = "select l.user_id as userId, l.date_time as dateTime, u.user_name as userName, "
            + "u.first_name as firstName, u.last_name as lastName, u.avatar_path as avatarPath, "
            + "replace(l.text, '\r', CHR(13)) as text, "
            + "(select round(avg(stars), 2) from rating where rating.user_id = l.user_id) as rating, "
            + "(select string_agg(comment, CHR(13)) from rating where rating.user_id = l.user_id) as comments, "
            + "(select count(*) from log l2 where l2.user_id = l.user_id and l2.chat_id = l.chat_id) as cnt "
            + "from log l left join users u on u.id = l.user_id "
            + "where l.chat_id = (select chat_id from chat_settings where setting_name = 'trip_channel' and setting_value = 'true' order by id limit 1) "
            + "and l.date_time >= :dateFrom "
            + "and l.date_time <= :dateTo "
            + "and (cast(:author as text) = '' or u.first_name ilike '%' || :author || '%' "
            + "or u.last_name ilike '%' || :author || '%' or u.user_name ilike '%' || :author || '%') "
            + "and (cast(:searchText as text) = '' or lower(l.text) like lower('%' || :searchText || '%')) "
            + "order by l.date_time desc limit :limit offset :offset", nativeQuery = true)
    List<TripViewDto> getTripChatWithRating(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("author") String author,
            @Param("searchText") String searchText);
}
