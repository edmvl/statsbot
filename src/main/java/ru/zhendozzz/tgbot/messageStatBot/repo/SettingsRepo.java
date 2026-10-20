package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.Settings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettingsRepo extends JpaRepository<Settings, Long> {
    Settings findByChatIdAndUserId(String chatId, String userId);

    List<Settings> findByChatId(String chatId);

    List<Settings> findByChatIdAndIsAdminIsTrue(String chatId);

    List<Settings> findByUserIdAndIsAdminIsTrue(String userId);
}
