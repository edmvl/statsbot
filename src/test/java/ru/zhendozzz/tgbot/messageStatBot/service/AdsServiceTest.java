package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.zhendozzz.tgbot.messageStatBot.entity.Ads;
import ru.zhendozzz.tgbot.messageStatBot.entity.AdsFile;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.AdsFileRepository;
import ru.zhendozzz.tgbot.messageStatBot.repo.AdsRepository;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.AdStatus;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdsServiceTest {

    @TempDir
    Path tempDir;

    private AdsRepository adsRepository;
    private AdsFileRepository adsFileRepository;
    private MessageExecutor messageExecutor;
    private SettingsService settingsService;
    private AdsService adsService;

    @BeforeEach
    void setUp() {
        adsRepository = mock(AdsRepository.class);
        adsFileRepository = mock(AdsFileRepository.class);
        messageExecutor = mock(MessageExecutor.class);
        settingsService = mock(SettingsService.class);
        when(settingsService.isEnabled(anyString(), any())).thenReturn(true);
        adsService = new AdsService(messageExecutor, adsRepository, adsFileRepository, settingsService);
        ReflectionTestUtils.setField(adsService, "uploadDir", tempDir.toString());
    }

    // --- публикация ---------------------------------------------------------

    @Test
    void publishesTextPostWithoutFiles() {
        Ads ad = ad(1L, "-100", null);
        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(ad));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(messageExecutor.sendMessageWithoutPreview("-100", "Текст")).thenReturn(555);

        adsService.sendAds();

        verify(messageExecutor).sendMessageWithoutPreview("-100", "Текст");
        verify(adsRepository).save(ad);
        assertThat(ad.getLastMessage()).isEqualTo("555");
    }

    @Test
    void publishesPostWithFiles() throws IOException {
        Ads ad = ad(2L, "-200", null);
        AdsFile file = adsFile(2L, "photo.jpg");
        createStoredFile(2L, "photo.jpg");

        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(ad));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(2L)).thenReturn(List.of(file));
        when(messageExecutor.sendMediaFiles(eq("-200"), eq("Текст"), anyList())).thenReturn("10,11");

        adsService.sendAds();

        verify(messageExecutor).sendMediaFiles(eq("-200"), eq("Текст"), anyList());
        verify(messageExecutor, never()).sendMessageWithoutPreview(any(), any());
        assertThat(ad.getLastMessage()).isEqualTo("10,11");
    }

    @Test
    void doesNotKeepBrokenLastMessageWhenSendFails() {
        Ads ad = ad(3L, "-300", null);
        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(ad));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(3L)).thenReturn(List.of());
        when(messageExecutor.sendMessageWithoutPreview("-300", "Текст")).thenReturn(null);

        adsService.sendAds();

        assertThat(ad.getLastMessage()).isNull();
        verify(adsRepository).save(ad);
    }

    @Test
    void removesAllMessagesOfFinishedPost() {
        Ads ad = ad(4L, "-400", "10,11");
        ad.setDateEnd(LocalDateTime.now().minusHours(1));
        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(ad));

        adsService.sendAds();

        verify(messageExecutor).deleteMessage(-400L, 10);
        verify(messageExecutor).deleteMessage(-400L, 11);
        verify(adsRepository).save(ad);
        assertThat(ad.getLastMessage()).isNull();
    }

    @Test
    void ignoresFinishedPostWithoutPublishedMessage() {
        Ads ad = ad(5L, "-500", null);
        ad.setDateEnd(LocalDateTime.now().minusHours(1));
        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(ad));

        adsService.sendAds();

        verify(messageExecutor, never()).deleteMessage(any(), any());
        verify(adsRepository, never()).save(any());
    }

    @Test
    void reconcilePublishesActivePostOnlyOnce() {
        Ads unpublished = ad(6L, "-600", null);
        Ads published = ad(7L, "-700", "42");
        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(unpublished, published));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(6L)).thenReturn(List.of());
        when(messageExecutor.sendMessageWithoutPreview("-600", "Текст")).thenReturn(7);

        adsService.reconcileAds();

        verify(messageExecutor, times(1)).sendMessageWithoutPreview("-600", "Текст");
        verify(messageExecutor, never()).sendMessageWithoutPreview(eq("-700"), any());
    }

    @Test
    void updateRepublishesActivePost() {
        Ads existing = ad(10L, "-1000", null);
        when(adsRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(10L)).thenReturn(List.of());
        when(adsRepository.save(any(Ads.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageExecutor.sendMessageWithoutPreview("-1000", "Новое")).thenReturn(77);

        Ads source = new Ads();
        source.setChatId("-1000");
        source.setMessage("Новое");

        Ads saved = adsService.updateAd(10L, source);

        verify(messageExecutor).sendMessageWithoutPreview("-1000", "Новое");
        assertThat(saved.getLastMessage()).isEqualTo("77");
    }

    // --- предложения и оплата ----------------------------------------------

    @Test
    void createProposalStoresPendingAd() {
        Ads[] saved = new Ads[1];
        when(adsRepository.countByUserIdAndStatusIn(eq("42"), anyList())).thenReturn(0L);
        when(adsRepository.save(any(Ads.class))).thenAnswer(invocation -> {
            Ads ad = invocation.getArgument(0);
            ad.setId(5L);
            saved[0] = ad;
            return ad;
        });
        when(adsRepository.findById(5L)).thenAnswer(invocation -> Optional.of(saved[0]));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(5L)).thenReturn(List.of());

        Ads proposal = adsService.createProposal("42", "-100", "Текст", 3, List.of());

        assertThat(proposal.getStatus()).isEqualTo(AdStatus.PENDING);
        assertThat(proposal.getDays()).isEqualTo(3);
        assertThat(proposal.getUserId()).isEqualTo("42");
        assertThat(proposal.getChatId()).isEqualTo("-100");
        verify(messageExecutor).sendMessage(any());
    }

    @Test
    void createProposalRejectsDisabledChat() {
        when(settingsService.isEnabled("-100", ChatSettingConstants.ENABLE_ADS)).thenReturn(false);

        assertThatThrownBy(() -> adsService.createProposal("42", "-100", "Текст", 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("реклама");
    }

    @Test
    void doesNotPublishInChatWithAdsDisabled() {
        Ads ad = ad(11L, "-1100", "5");
        when(adsRepository.findByStatus(AdStatus.ACTIVE)).thenReturn(List.of(ad));
        when(settingsService.isEnabled("-1100", ChatSettingConstants.ENABLE_ADS)).thenReturn(false);

        adsService.sendAds();

        verify(messageExecutor).deleteMessage(-1100L, 5);
        verify(messageExecutor, never()).sendMessageWithoutPreview(any(), any());
        assertThat(ad.getLastMessage()).isNull();
    }

    @Test
    void createProposalRejectsTooManyOpenProposals() {
        when(adsRepository.countByUserIdAndStatusIn(eq("42"), anyList())).thenReturn(3L);

        assertThatThrownBy(() -> adsService.createProposal("42", "-100", "Текст", 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createProposalRejectsInvalidDays() {
        assertThatThrownBy(() -> adsService.createProposal("42", "-100", "Текст", 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveMovesProposalToApproved() {
        Ads ad = proposal(3L, AdStatus.PENDING);
        when(adsRepository.findById(3L)).thenReturn(Optional.of(ad));
        when(adsRepository.save(any(Ads.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(3L)).thenReturn(List.of());

        Ads saved = adsService.approveProposal(3L);

        assertThat(saved.getStatus()).isEqualTo(AdStatus.APPROVED);
        verify(messageExecutor).sendMessage(any());
    }

    @Test
    void approveRejectsAlreadyProcessedProposal() {
        Ads ad = proposal(3L, AdStatus.APPROVED);
        when(adsRepository.findById(3L)).thenReturn(Optional.of(ad));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(3L)).thenReturn(List.of());

        assertThatThrownBy(() -> adsService.approveProposal(3L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void priceIsPerDay() {
        Ads ad = proposal(4L, AdStatus.PENDING);
        ad.setDays(5);

        assertThat(adsService.priceOf(ad)).isEqualTo(5 * AdStatus.STARS_PER_DAY);
    }

    @Test
    void successfulPaymentActivatesAndPublishesAd() {
        Ads ad = proposal(7L, AdStatus.APPROVED);
        ad.setDays(2);
        when(adsRepository.findById(7L)).thenReturn(Optional.of(ad));
        when(adsRepository.save(any(Ads.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adsFileRepository.findByAdsIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(messageExecutor.sendMessageWithoutPreview("-700", "Реклама")).thenReturn(99);

        adsService.handleSuccessfulPayment("ad:7", 200, "charge-1");

        assertThat(ad.getStatus()).isEqualTo(AdStatus.ACTIVE);
        assertThat(ad.getStarsPaid()).isEqualTo(200);
        assertThat(ad.getPaymentChargeId()).isEqualTo("charge-1");
        assertThat(ad.getDateStart()).isNotNull();
        assertThat(ad.getLastMessage()).isEqualTo("99");
    }

    @Test
    void ignoresPaymentForUnknownProposal() {
        adsService.handleSuccessfulPayment("nonsense", 100, "charge-2");

        verify(adsRepository, never()).save(any());
    }

    // --- вложения -----------------------------------------------------------

    @Test
    void uploadFilesRejectsMoreThanMax() {
        Ads ad = ad(8L, "-800", null);
        when(adsRepository.findById(8L)).thenReturn(Optional.of(ad));
        when(adsFileRepository.countByAdsId(8L)).thenReturn(2L);

        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes()),
                new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes()));

        assertThatThrownBy(() -> adsService.uploadFiles(8L, files))
                .isInstanceOf(IllegalArgumentException.class);
        verify(adsFileRepository, never()).save(any());
    }

    @Test
    void uploadFilesStoresFilesOnDisk() throws IOException {
        Ads ad = ad(9L, "-900", null);
        when(adsRepository.findById(9L)).thenReturn(Optional.of(ad));
        when(adsFileRepository.countByAdsId(9L)).thenReturn(0L);

        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes()));

        adsService.uploadFiles(9L, files);

        verify(adsFileRepository).save(any(AdsFile.class));
        try (var walk = Files.walk(tempDir.resolve("ads").resolve("9"))) {
            assertThat(walk.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    // --- helpers ------------------------------------------------------------

    private Ads ad(Long id, String chatId, String lastMessage) {
        Ads ad = new Ads();
        ad.setId(id);
        ad.setChatId(chatId);
        ad.setMessage("Текст");
        ad.setLastMessage(lastMessage);
        ad.setStatus(AdStatus.ACTIVE);
        return ad;
    }

    private Ads proposal(Long id, String status) {
        Ads ad = new Ads();
        ad.setId(id);
        ad.setChatId("-700");
        ad.setMessage("Реклама");
        ad.setUserId("42");
        ad.setDays(1);
        ad.setStatus(status);
        return ad;
    }

    private AdsFile adsFile(Long adsId, String storedName) {
        AdsFile file = new AdsFile();
        file.setId(100L);
        file.setAdsId(adsId);
        file.setOriginalName(storedName);
        file.setStoredName(storedName);
        return file;
    }

    private void createStoredFile(Long adsId, String name) throws IOException {
        Path dir = tempDir.resolve("ads").resolve(String.valueOf(adsId));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), "content");
    }
}
