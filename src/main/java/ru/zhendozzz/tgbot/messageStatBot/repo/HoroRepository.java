package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.Horo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HoroRepository extends CrudRepository<Horo, Long> {
    List<Horo> getAllByDateAndSign(LocalDate localDate, String sign);
}
