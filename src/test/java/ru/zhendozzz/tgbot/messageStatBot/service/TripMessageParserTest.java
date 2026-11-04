package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import ru.zhendozzz.tgbot.messageStatBot.dto.TripMessageInfo;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TripMessageParserTest {

    private final TripMessageParser parser = new TripMessageParser();
    private final LocalDateTime sentAt = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    void recognizesPointsByArrowAndFutureTime() {
        TripMessageInfo info = parser.parse("Чебоксары → Урмары в 18:30, 3 места", sentAt);

        assertThat(info.from()).isEqualTo("Чебоксары");
        assertThat(info.to()).isEqualTo("Урмары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 18, 30));
    }

    @Test
    void recognizesPointsWhenDestinationComesFirst() {
        TripMessageInfo info = parser.parse("В Урмары из Чебоксар в 7:30", sentAt);

        assertThat(info.from()).isEqualTo("Чебоксары");
        assertThat(info.to()).isEqualTo("Урмары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 7, 30));
    }

    @Test
    void recognizesNovocheboksarskAsSeparatePoint() {
        TripMessageInfo info = parser.parse("Новочебоксарск-Урмары завтра в 6-30", sentAt);

        assertThat(info.from()).isEqualTo("Новочебоксарск");
        assertThat(info.to()).isEqualTo("Урмары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 6, 30));
    }

    @Test
    void recognizesTomorrowAndTimeWithHyphen() {
        TripMessageInfo info = parser.parse("Урмары-Чебоксары завтра в 6-30", sentAt);

        assertThat(info.from()).isEqualTo("Урмары");
        assertThat(info.to()).isEqualTo("Чебоксары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 6, 30));
    }

    @Test
    void recognizesMonthDateAndDayPart() {
        TripMessageInfo info = parser.parse("15 октября из Чебоксар в Урмары в 7 вечера",
                LocalDateTime.of(2026, 10, 10, 9, 0));

        assertThat(info.from()).isEqualTo("Чебоксары");
        assertThat(info.to()).isEqualTo("Урмары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 15, 19, 0));
    }

    @Test
    void recognizesWeekday() {
        TripMessageInfo info = parser.parse("В пятницу в 8 утра Урмары → Чебоксары", sentAt);

        assertThat(info.from()).isEqualTo("Урмары");
        assertThat(info.to()).isEqualTo("Чебоксары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 23, 8, 0));
    }

    @Test
    void infersOnlyDeparturePoint() {
        TripMessageInfo info = parser.parse("Выезжаю из Чебоксар в 19:00", sentAt);

        assertThat(info.from()).isEqualTo("Чебоксары");
        assertThat(info.to()).isNull();
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0));
    }

    @Test
    void infersOnlyDestinationPoint() {
        TripMessageInfo info = parser.parse("В Урмары в 19:00", sentAt);

        assertThat(info.from()).isNull();
        assertThat(info.to()).isEqualTo("Урмары");
        assertThat(info.tripTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0));
    }

    @Test
    void leavesUnrecognizedMessagesWithoutPoints() {
        TripMessageInfo info = parser.parse("Кто едет завтра? Возьму попутчиков", sentAt);

        assertThat(info.from()).isNull();
        assertThat(info.to()).isNull();
        assertThat(info.tripTime()).isNull();
    }

    @Test
    void marksPassengerLookingForCar() {
        TripMessageInfo info = parser.parse("Ищу машину до Чебоксар в 18:00", sentAt);

        assertThat(info.seekingCar()).isTrue();
        assertThat(info.to()).isEqualTo("Чебоксары");
    }

    @Test
    void marksDriverOfferingSeatsAsNotSeeking() {
        TripMessageInfo info = parser.parse("Ищу попутчиков, есть места, выезжаю в 18:00", sentAt);

        assertThat(info.seekingCar()).isFalse();
    }

    @Test
    void doesNotMarkDriverAsSeekingEvenWithQuestion() {
        TripMessageInfo info = parser.parse("Кто со мной? Есть 3 места, еду из Чебоксар в Урмары", sentAt);

        assertThat(info.seekingCar()).isFalse();
        assertThat(info.from()).isEqualTo("Чебоксары");
        assertThat(info.to()).isEqualTo("Урмары");
    }

    @Test
    void marksRequestToGiveLiftAsSeeking() {
        TripMessageInfo info = parser.parse("Кто может подвезти из Урмар в Чебоксары после 18:00?", sentAt);

        assertThat(info.seekingCar()).isTrue();
    }

    @Test
    void marksWhoIsGoingAsSeekingWhenNoDriverPhrases() {
        TripMessageInfo info = parser.parse("Кто едет в Чебоксары в 18:00?", sentAt);

        assertThat(info.seekingCar()).isTrue();
        assertThat(info.to()).isEqualTo("Чебоксары");
    }

    @Test
    void doesNotGuessPointWithoutPreposition() {
        TripMessageInfo info = parser.parse("Чебоксары 8:00", sentAt);

        assertThat(info.from()).isNull();
        assertThat(info.to()).isNull();
        assertThat(info.tripTime()).isNotNull();
    }
}
