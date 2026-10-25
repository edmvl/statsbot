package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.entity.Quest;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestCheckpoint;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestParticipant;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestPass;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestCheckpointRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestParticipantRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestPassRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.QuestRepo;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestProgress;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestServiceTest {

    private final QuestRepo questRepo = mock(QuestRepo.class);
    private final QuestCheckpointRepo checkpointRepo = mock(QuestCheckpointRepo.class);
    private final QuestParticipantRepo participantRepo = mock(QuestParticipantRepo.class);
    private final QuestPassRepo passRepo = mock(QuestPassRepo.class);
    private final FileLoaderService fileLoaderService = mock(FileLoaderService.class);

    private final QuestService service = new QuestService(
            questRepo, checkpointRepo, participantRepo, passRepo, fileLoaderService);

    @Test
    void distanceIsZeroForSamePoint() {
        assertThat(QuestService.distanceMeters(55.75, 37.61, 55.75, 37.61)).isZero();
    }

    @Test
    void distanceForOneDegreeOfLatitudeIsAbout111Km() {
        double distance = QuestService.distanceMeters(0, 0, 1, 0);

        assertThat(distance).isBetween(111_000d, 111_500d);
    }

    @Test
    void joinCreatesParticipant() {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.empty());
        when(participantRepo.save(any(QuestParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuestParticipant participant = service.join(3L, "777");

        assertThat(participant.getQuestId()).isEqualTo(3L);
        assertThat(participant.getUserId()).isEqualTo("777");
        assertThat(participant.getStartedAt()).isNotNull();
        assertThat(participant.getFinishedAt()).isNull();
    }

    @Test
    void joinIsIdempotent() {
        QuestParticipant existing = participant(1L);
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.of(existing));

        assertThat(service.join(3L, "777")).isSameAs(existing);
        verify(participantRepo, never()).save(any(QuestParticipant.class));
    }

    @Test
    void joinRejectsInactiveQuest() {
        Quest quest = activeQuest();
        quest.setActive(false);
        when(questRepo.findById(3L)).thenReturn(Optional.of(quest));

        assertThatThrownBy(() -> service.join(3L, "777"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void answerRequiresParticipation() {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.empty());

        assertThat(service.answer(3L, "777", 5L, "ответ", 55.75, 37.61))
                .isEqualTo(QuestService.AnswerResult.NOT_JOINED);
        verify(passRepo, never()).save(any(QuestPass.class));
    }

    @Test
    void answerRejectsFarPoint() {
        stubParticipantAndCheckpoint(checkpoint(5L, 55.75, 37.61, 50, "Урмары"));

        // ~1.1 км от точки при радиусе 50 м
        assertThat(service.answer(3L, "777", 5L, "Урмары", 55.76, 37.61))
                .isEqualTo(QuestService.AnswerResult.TOO_FAR);
        verify(passRepo, never()).save(any(QuestPass.class));
    }

    @Test
    void answerRejectsWrongText() {
        stubParticipantAndCheckpoint(checkpoint(5L, 55.75, 37.61, 50, "Урмары"));

        assertThat(service.answer(3L, "777", 5L, "Чебоксары", 55.75, 37.61))
                .isEqualTo(QuestService.AnswerResult.WRONG);
        verify(passRepo, never()).save(any(QuestPass.class));
    }

    @Test
    void answerIsCaseAndSpaceInsensitive() {
        stubParticipantAndCheckpoint(checkpoint(5L, 55.75, 37.61, 50, "Урмары"));
        when(passRepo.countByParticipantId(1L)).thenReturn(1L);
        when(checkpointRepo.countByQuestId(3L)).thenReturn(2L);

        assertThat(service.answer(3L, "777", 5L, "  УРМАРЫ  ", 55.75, 37.61))
                .isEqualTo(QuestService.AnswerResult.OK);

        ArgumentCaptor<QuestPass> pass = ArgumentCaptor.forClass(QuestPass.class);
        verify(passRepo).save(pass.capture());
        assertThat(pass.getValue().getCheckpointId()).isEqualTo(5L);
        assertThat(pass.getValue().getParticipantId()).isEqualTo(1L);
        // пройден 1 из 2 — квест ещё не завершён
        verify(participantRepo, never()).save(any(QuestParticipant.class));
    }

    @Test
    void answerFinishesQuestOnLastCheckpoint() {
        stubParticipantAndCheckpoint(checkpoint(5L, 55.75, 37.61, 50, "Урмары"));
        QuestParticipant participant = participant(1L);
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.of(participant));
        when(passRepo.countByParticipantId(1L)).thenReturn(1L);
        when(checkpointRepo.countByQuestId(3L)).thenReturn(1L);

        assertThat(service.answer(3L, "777", 5L, "Урмары", 55.75, 37.61))
                .isEqualTo(QuestService.AnswerResult.OK);

        assertThat(participant.getFinishedAt()).isNotNull();
        verify(participantRepo).save(participant);
    }

    @Test
    void answerTwiceIsRejected() {
        stubParticipantAndCheckpoint(checkpoint(5L, 55.75, 37.61, 50, "Урмары"));
        when(passRepo.findByParticipantIdAndCheckpointId(1L, 5L)).thenReturn(Optional.of(new QuestPass()));

        assertThat(service.answer(3L, "777", 5L, "Урмары", 55.75, 37.61))
                .isEqualTo(QuestService.AnswerResult.ALREADY);
    }

    @Test
    void answerWithoutCoordinatesIsTooFar() {
        stubParticipantAndCheckpoint(checkpoint(5L, 55.75, 37.61, 50, "Урмары"));

        assertThat(service.answer(3L, "777", 5L, "Урмары", null, null))
                .isEqualTo(QuestService.AnswerResult.TOO_FAR);
    }

    @Test
    void addCheckpointValidatesQuestion() {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));

        assertThatThrownBy(() -> service.addCheckpoint(3L, "Точка", "  ", "ответ", "55.75", "37.61", "50", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(checkpointRepo, never()).save(any(QuestCheckpoint.class));
    }

    @Test
    void addCheckpointRequiresValidCoordinates() {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));

        assertThatThrownBy(() -> service.addCheckpoint(3L, "Точка", "Вопрос", "ответ", "95", "37.61", "50", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addCheckpointStoresMediaInOwnDirectory() {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));
        when(checkpointRepo.countByQuestId(3L)).thenReturn(0L);
        when(checkpointRepo.save(any(QuestCheckpoint.class))).thenAnswer(invocation -> {
            QuestCheckpoint checkpoint = invocation.getArgument(0);
            if (checkpoint.getId() == null) {
                checkpoint.setId(5L);
            }
            return checkpoint;
        });
        MultipartFile media = mock(MultipartFile.class);
        when(media.isEmpty()).thenReturn(false);
        when(media.getContentType()).thenReturn("image/jpeg");
        when(media.getOriginalFilename()).thenReturn("hint.jpg");
        when(fileLoaderService.saveSingleFile(media, "quest-5")).thenReturn("123_hint.jpg");

        QuestCheckpoint checkpoint = service.addCheckpoint(
                3L, "Беседка", "Что написано?", "Урмары", "55.75", "37.61", "80", media);

        assertThat(checkpoint.getTitle()).isEqualTo("Беседка");
        assertThat(checkpoint.getRadiusM()).isEqualTo(80);
        assertThat(checkpoint.getMediaType()).isEqualTo("image");
        assertThat(checkpoint.getMediaFile()).isEqualTo("123_hint.jpg");
        verify(fileLoaderService).saveSingleFile(media, "quest-5");
    }

    @Test
    void addCheckpointRejectsUnsupportedMedia() {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));
        when(checkpointRepo.countByQuestId(3L)).thenReturn(0L);
        when(checkpointRepo.save(any(QuestCheckpoint.class))).thenAnswer(invocation -> {
            QuestCheckpoint checkpoint = invocation.getArgument(0);
            checkpoint.setId(5L);
            return checkpoint;
        });
        MultipartFile media = mock(MultipartFile.class);
        when(media.isEmpty()).thenReturn(false);
        when(media.getContentType()).thenReturn("application/pdf");
        when(media.getOriginalFilename()).thenReturn("doc.pdf");

        assertThatThrownBy(() -> service.addCheckpoint(
                3L, null, "Вопрос", "ответ", "55.75", "37.61", "50", media))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressCountsPassedCheckpoints() {
        QuestPass pass = new QuestPass();
        pass.setCheckpointId(5L);
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.of(participant(1L)));
        when(passRepo.findByParticipantId(1L)).thenReturn(List.of(pass));
        when(checkpointRepo.countByQuestId(3L)).thenReturn(3L);

        QuestProgress progress = service.progress(3L, "777");

        assertThat(progress.isJoined()).isTrue();
        assertThat(progress.isFinished()).isFalse();
        assertThat(progress.getPassedCheckpointIds()).isEqualTo(Set.of(5L));
        assertThat(progress.getPassed()).isEqualTo(1);
        assertThat(progress.getTotal()).isEqualTo(3);
    }

    @Test
    void progressForNonParticipantIsEmpty() {
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.empty());
        when(checkpointRepo.countByQuestId(3L)).thenReturn(2L);

        QuestProgress progress = service.progress(3L, "777");

        assertThat(progress.isJoined()).isFalse();
        assertThat(progress.getPassedCheckpointIds()).isEmpty();
        assertThat(progress.getTotal()).isEqualTo(2);
    }

    @Test
    void deleteCheckpointClearsMediaDirectory() {
        QuestCheckpoint checkpoint = checkpoint(5L, 55.75, 37.61, 50, "ответ");
        when(checkpointRepo.findByIdAndQuestId(5L, 3L)).thenReturn(Optional.of(checkpoint));

        service.deleteCheckpoint(3L, 5L);

        verify(fileLoaderService).clearFiles("quest-5");
        verify(checkpointRepo).delete(checkpoint);
    }

    private void stubParticipantAndCheckpoint(QuestCheckpoint checkpoint) {
        when(questRepo.findById(3L)).thenReturn(Optional.of(activeQuest()));
        when(participantRepo.findByQuestIdAndUserId(3L, "777")).thenReturn(Optional.of(participant(1L)));
        when(checkpointRepo.findByIdAndQuestId(checkpoint.getId(), 3L)).thenReturn(Optional.of(checkpoint));
        when(passRepo.findByParticipantIdAndCheckpointId(1L, checkpoint.getId())).thenReturn(Optional.empty());
    }

    private Quest activeQuest() {
        Quest quest = new Quest();
        quest.setId(3L);
        quest.setTitle("Тайны парка");
        quest.setActive(true);
        return quest;
    }

    private QuestCheckpoint checkpoint(Long id, double lat, double lon, int radius, String answer) {
        QuestCheckpoint checkpoint = new QuestCheckpoint();
        checkpoint.setId(id);
        checkpoint.setQuestId(3L);
        checkpoint.setTitle("Точка");
        checkpoint.setQuestion("Что написано?");
        checkpoint.setAnswer(answer);
        checkpoint.setLat(lat);
        checkpoint.setLon(lon);
        checkpoint.setRadiusM(radius);
        return checkpoint;
    }

    private QuestParticipant participant(Long id) {
        QuestParticipant participant = new QuestParticipant();
        participant.setId(id);
        participant.setQuestId(3L);
        participant.setUserId("777");
        return participant;
    }
}
