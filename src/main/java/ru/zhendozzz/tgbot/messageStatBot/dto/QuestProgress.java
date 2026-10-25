package ru.zhendozzz.tgbot.messageStatBot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/** Прогресс участника по квесту. */
@Getter
@AllArgsConstructor
public class QuestProgress {

    /** Пользователь нажал «Участвовать». */
    private final boolean joined;

    /** Пройдены все чекпоинты. */
    private final boolean finished;

    /** id пройденных чекпоинтов. */
    private final Set<Long> passedCheckpointIds;

    private final int passed;

    private final int total;
}
