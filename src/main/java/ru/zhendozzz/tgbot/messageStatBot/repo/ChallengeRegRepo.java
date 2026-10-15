package ru.zhendozzz.tgbot.messageStatBot.repo;

import ru.zhendozzz.tgbot.messageStatBot.entity.ChallengeReg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChallengeRegRepo extends JpaRepository<ChallengeReg, Long> {
    ChallengeReg findByChallengeIdAndAndUserId(Long ChallengeId, String userId);
    List<ChallengeReg> findByChallengeId(Long ChallengeId);
}
