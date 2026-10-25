package ru.zhendozzz.tgbot.messageStatBot.dto;

import java.time.LocalDateTime;

/** Участник квеста для страницы результатов (данные из quest_participant + users). */
public interface QuestParticipantView {

    Long getParticipantId();

    String getUserId();

    String getFirstName();

    String getLastName();

    String getUserName();

    String getAvatarPath();

    LocalDateTime getStartedAt();

    /** {@code null}, пока квест не пройден. */
    LocalDateTime getFinishedAt();

    /** Сколько чекпоинтов пройдено. */
    Long getPassed();
}
