package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.StickerPack;

@Repository
public interface StickerPackRepo extends JpaRepository<StickerPack, String> {

    /** Самый свежий набор, в который ещё можно добавлять стикеры. */
    StickerPack findFirstByStickersCountLessThanOrderByCreatedAtDesc(Integer maxCount);
}
