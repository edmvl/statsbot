package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.dto.DriverReviewDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.Rating;

import java.util.List;

@Repository
public interface RatingRepo extends JpaRepository<Rating, Long> {

    /**
     * Все отзывы о пользователе-водителе с данными автора отзыва (из {@code users}).
     */
    @Query(value = "select r.id as id, r.stars as stars, r.comment as comment, "
            + "r.review_user_id as reviewUserId, "
            + "u.first_name as firstName, u.last_name as lastName, u.user_name as userName, "
            + "u.avatar_path as avatarPath "
            + "from rating r left join users u on u.id = r.review_user_id "
            + "where r.user_id = ?1 order by r.id desc", nativeQuery = true)
    List<DriverReviewDto> findDriverReviews(String userId);
}
