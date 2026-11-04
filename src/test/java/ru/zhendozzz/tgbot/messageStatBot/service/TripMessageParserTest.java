package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripMessageInfo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.TripDirection;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TripMessageParserTest {

    private final TripMessageParser parser = new TripMessageParser();
    private final LocalDateTime sentAt = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    void recognizesDirectionByArrowAndFutureTime() {
        TripMessageInfo info = parser.parse("Чебоксары → Урмары в 18:30, 3 места", sentAt);

        assertThat(info.direction()).isEqualTo(TripDirection.CHEBOKSARY_URMARY);
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 18, 30));
    }

    @Test
    void recognizesDirectionWhenDestinationComesFirst() {
        TripMessageInfo info = parser.parse("В Урмары из Чебоксар в 7:30", sentAt);

        assertThat(info.direction()).isEqualTo(TripDirection.CHEBOKSARY_URMARY);
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 7, 30));
    }

    @Test
    void recognizesTomorrowAndTimeWithHyphen() {
        TripMessageInfo info = parser.parse("Урмары-Чебоксары завтра в 6-30", sentAt);

        assertThat(info.direction()).isEqualTo(TripDirection.URMARY_CHEBOKSARY);
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 6, 30));
    }

    @Test
    void recognizesMonthDateAndDayPart() {
        TripMessageInfo info = parser.parse("15 октября из Чебоксар в Урмары в 7 вечера",
                LocalDateTime.of(2026, 10, 10, 9, 0));

        assertThat(info.direction()).isEqualTo(TripDirection.CHEBOKSARY_URMARY);
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 15, 19, 0));
    }

    @Test
    void recognizesWeekday() {
        TripMessageInfo info = parser.parse("В пятницу в 8 утра Урмары → Чебоксары", sentAt);

        assertThat(info.direction()).isEqualTo(TripDirection.URMARY_CHEBOKSARY);
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 8, 0));
    }

    @Test
    void infersDirectionFromSingleDepartureCity() {
        TripMessageInfo info = parser.parse("Выезжаю из Чебоксар в 19:00", sentAt);

        assertThat(info.direction()).isEqualTo(TripDirection.CHEBOKSARY_URMARY);
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0));
    }

    @Test
    void leavesUnrecognizedMessagesWithoutDirection() {
        TripMessageInfo info = parser.parse("Кто едет завтра? Возьму попутчиков", sentAt);

        assertThat(info.direction()).isNull();
        assertThat(info.tripTime()).isNull();
    }

    @Test
    void doesNotGuessDirectionFromOneCityWithoutPreposition() {
        TripMessageInfo info = parser.parse("Чебоксары 8:00", sentAt);

        assertThat(info.direction()).isNull();
        assertThat(info.tripTime()).isNotNull();
    }
}
