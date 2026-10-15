package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.AdsFile;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsFileRepository extends JpaRepository<AdsFile, Long> {

    List<AdsFile> findByAdsIdOrderByIdAsc(Long adsId);

    long countByAdsId(Long adsId);

    Optional<AdsFile> findByIdAndAdsId(Long id, Long adsId);
}
