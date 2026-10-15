package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.Selected;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface SelectedRepo extends JpaRepository<Selected, Long> {

    Selected findByChatIdAndDate(String chatId, LocalDate date);

}
