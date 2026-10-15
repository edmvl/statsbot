package ru.zhendozzz.tgbot.messageStatBot.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.zhendozzz.tgbot.messageStatBot.entity.Prediction;

@Repository
public interface PredictionRepository extends CrudRepository<Prediction, Long> {
    public Prediction getPredictionById(long i);
}
