package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSettingsRepo extends JpaRepository<ChatSettings, Long> {
    ChatSettings findFirstByChatIdAndSettingName(String chatId, String settingName);
    List<ChatSettings> findBySettingName(String settingName);
    ChatSettings findFirstBySettingNameAndSettingValueOrderByIdAsc(String settingName, String settingValue);
}
