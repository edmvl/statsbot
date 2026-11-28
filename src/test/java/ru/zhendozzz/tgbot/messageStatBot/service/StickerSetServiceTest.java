package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.zhendozzz.tgbot.messageStatBot.entity.StickerPack;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.StickerPackRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StickerSetServiceTest {

    private static final byte[] PNG = {1, 2, 3};
    private static final String EMOJI = "😍";
    private static final String PACK = "hamoryal_by_hamoryalstatsbot";

    private StickerPackRepo stickerPackRepo;
    private MessageExecutor messageExecutor;
    private StickerUploader stickerUploader;
    private StickerSetService service;

    @BeforeEach
    void setUp() {
        stickerPackRepo = mock(StickerPackRepo.class);
        messageExecutor = mock(MessageExecutor.class);
        stickerUploader = mock(StickerUploader.class);
        service = new StickerSetService(stickerPackRepo, messageExecutor, stickerUploader);
        ReflectionTestUtils.setField(service, "botUsername", "hamoryalstatsbot");
    }

    @Test
    void createsFirstSetNamedAfterBot() {
        when(stickerPackRepo.findFirstByStickersCountLessThanOrderByCreatedAtDesc(StickerSetService.MAX_STICKERS))
                .thenReturn(null);
        when(stickerPackRepo.count()).thenReturn(0L);
        when(messageExecutor.createStickerSet(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn("file-1");

        StickerSetService.Result result = service.addSticker(PNG, EMOJI);

        assertThat(result.getName()).isEqualTo(PACK);
        assertThat(result.isCreated()).isTrue();
        assertThat(result.getCount()).isEqualTo(1);
        assertThat(result.getFileId()).isEqualTo("file-1");
        verify(messageExecutor).createStickerSet(
                eq(AccessConstants.SUPER_ADMIN_ID), eq(PACK), eq("hamoryal"), eq(PNG), eq(EMOJI));

        ArgumentCaptor<StickerPack> saved = ArgumentCaptor.forClass(StickerPack.class);
        verify(stickerPackRepo).save(saved.capture());
        assertThat(saved.getValue().getStickersCount()).isEqualTo(1);
    }

    @Test
    void addsStickerToExistingSet() {
        StickerPack pack = pack(PACK, 5);
        when(stickerPackRepo.findFirstByStickersCountLessThanOrderByCreatedAtDesc(StickerSetService.MAX_STICKERS))
                .thenReturn(pack);
        when(stickerUploader.addStickerToSet(AccessConstants.SUPER_ADMIN_ID, PACK, EMOJI, PNG)).thenReturn(true);
        when(messageExecutor.getLastStickerFileId(PACK)).thenReturn("file-2");

        StickerSetService.Result result = service.addSticker(PNG, EMOJI);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.getCount()).isEqualTo(6);
        assertThat(result.getFileId()).isEqualTo("file-2");
        verify(stickerUploader).addStickerToSet(AccessConstants.SUPER_ADMIN_ID, PACK, EMOJI, PNG);
        verify(stickerPackRepo).save(pack);
    }

    @Test
    void createsSecondSetWhenFirstIsFull() {
        when(stickerPackRepo.findFirstByStickersCountLessThanOrderByCreatedAtDesc(StickerSetService.MAX_STICKERS))
                .thenReturn(null);
        when(stickerPackRepo.count()).thenReturn(1L);
        when(messageExecutor.createStickerSet(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn("file-1");

        StickerSetService.Result result = service.addSticker(PNG, EMOJI);

        assertThat(result.getName()).isEqualTo("hamoryal_2_by_hamoryalstatsbot");
        assertThat(result.isCreated()).isTrue();
    }

    @Test
    void buildsNamesWithBotSuffix() {
        assertThat(service.nameFor(1)).isEqualTo(PACK);
        assertThat(service.nameFor(2)).isEqualTo("hamoryal_2_by_hamoryalstatsbot");
        assertThat(service.nameFor(3)).isEqualTo("hamoryal_3_by_hamoryalstatsbot");
    }

    @Test
    void reportsTelegramFailureOnCreate() {
        when(stickerPackRepo.findFirstByStickersCountLessThanOrderByCreatedAtDesc(StickerSetService.MAX_STICKERS))
                .thenReturn(null);
        when(stickerPackRepo.count()).thenReturn(0L);
        when(messageExecutor.createStickerSet(anyLong(), anyString(), anyString(), any(), anyString()))
                .thenReturn(null);

        assertThatThrownBy(() -> service.addSticker(PNG, EMOJI))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsTelegramFailureOnAdd() {
        StickerPack pack = pack(PACK, 5);
        when(stickerPackRepo.findFirstByStickersCountLessThanOrderByCreatedAtDesc(StickerSetService.MAX_STICKERS))
                .thenReturn(pack);
        when(stickerUploader.addStickerToSet(anyLong(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.addSticker(PNG, EMOJI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PACK);
    }

    private StickerPack pack(String name, int count) {
        StickerPack pack = new StickerPack();
        pack.setName(name);
        pack.setTitle("hamoryal");
        pack.setStickersCount(count);
        return pack;
    }
}
