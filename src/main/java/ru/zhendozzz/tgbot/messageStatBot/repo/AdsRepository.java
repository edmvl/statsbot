package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.Ads;

import java.util.Collection;
import java.util.List;

@Repository
public interface AdsRepository extends JpaRepository<Ads, Long> {

    List<Ads> findByStatus(String status);

    List<Ads> findByStatusInOrderByIdDesc(Collection<String> statuses);

    List<Ads> findByUserIdAndStatusInOrderByIdDesc(String userId, Collection<String> statuses);

    long countByUserIdAndStatusIn(String userId, Collection<String> statuses);
}
