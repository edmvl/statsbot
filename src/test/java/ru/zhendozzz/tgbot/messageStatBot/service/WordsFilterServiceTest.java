package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zhendozzz.tgbot.messageStatBot.dto.MessageDTO;
import ru.zhendozzz.tgbot.messageStatBot.entity.WordsFilter;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.WordsFilterRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.AccessConstants;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WordsFilterServiceTest {

    /** Telegram-бот, от имени которого пишут анонимные администраторы. */
    private static final long GROUP_ANONYMOUS_BOT_ID = 1087968824L;

    private MessageExecutor messageExecutor;
    private WordsFilterRepo wordsFilterRepo;
    private SettingsService settingsService;
    private LogService logService;
    private WordsFilterService service;

    @BeforeEach
    void setUp() {
        messageExecutor = mock(MessageExecutor.class);
        wordsFilterRepo = mock(WordsFilterRepo.class);
        settingsService = mock(SettingsService.class);
        logService = mock(LogService.class);
        service = new WordsFilterService(messageExecutor, wordsFilterRepo, settingsService, logService);

        when(wordsFilterRepo.findAll()).thenReturn(List.of());
        when(settingsService.isEnabled(anyString(), eq(ChatSettingConstants.TRIP_CHANNEL))).thenReturn(true);
        when(settingsService.isUserAdmin(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void keepsLinksFromSuperAdmin() {
        service.deleteByFilter(message(AccessConstants.SUPER_ADMIN_ID), linkUpdate());

        verify(messageExecutor, never()).deleteMessage(any(), any());
    }

    @Test
    void keepsLinksFromAnonymousAdmin() {
        service.deleteByFilter(message(GROUP_ANONYMOUS_BOT_ID), linkUpdate());

        verify(messageExecutor, never()).deleteMessage(any(), any());
    }

    @Test
    void keepsLinksFromChatAdmin() {
        when(settingsService.isUserAdmin(-100L, 777L)).thenReturn(true);

        service.deleteByFilter(message(777L), linkUpdate());

        verify(messageExecutor, never()).deleteMessage(any(), any());
    }

    @Test
    void deletesLinksFromRegularUser() {
        service.deleteByFilter(message(777L), linkUpdate());

        verify(messageExecutor).deleteMessage(-100L, 5);
    }

    @Test
    void deletesBannedWordFromRegularUser() {
        WordsFilter banned = new WordsFilter();
        banned.setWord("мат");
        when(wordsFilterRepo.findAll()).thenReturn(List.of(banned));

        Update update = new Update();
        update.setMessage(new Message());
        MessageDTO dto = MessageDTO.builder()
                .chatId(-100L)
                .messageId(6)
                .userId(777L)
                .userText("тут мат")
                .build();

        service.deleteByFilter(dto, update);

        verify(messageExecutor).deleteMessage(-100L, 6);
        // В истории чата сообщение должно остаться с пометкой «удалено».
        verify(logService).markDeleted(-100L, 6);
    }

    @Test
    void keepsBannedWordFromSuperAdmin() {
        WordsFilter banned = new WordsFilter();
        banned.setWord("мат");
        when(wordsFilterRepo.findAll()).thenReturn(List.of(banned));

        Update update = new Update();
        update.setMessage(new Message());
        MessageDTO dto = MessageDTO.builder()
                .chatId(-100L)
                .messageId(7)
                .userId(AccessConstants.SUPER_ADMIN_ID)
                .userText("тут мат")
                .build();

        service.deleteByFilter(dto, update);

        verify(messageExecutor, never()).deleteMessage(any(), any());
    }

    /** Список стоп-слов кэшируется: findAll() на каждое сообщение — лишний запрос и мусор в куче. */
    @Test
    void loadsWordsOnceForSeveralMessages() {
        WordsFilter banned = new WordsFilter();
        banned.setWord("мат");
        when(wordsFilterRepo.findAll()).thenReturn(List.of(banned));

        service.processMessage(messageWithText(10, 777L, "тут мат"), plainUpdate());
        service.processMessage(messageWithText(11, 777L, "и снова мат"), plainUpdate());

        verify(wordsFilterRepo, times(1)).findAll();
        verify(messageExecutor).deleteMessage(-100L, 10);
        verify(messageExecutor).deleteMessage(-100L, 11);
    }

    /** Новое слово должно сразу попадать в фильтр — иначе кэш держит устаревший список. */
    @Test
    void addingWordDropsCachedList() {
        when(wordsFilterRepo.findAll()).thenReturn(List.of());
        // обычный пользователь → админ в addWord → снова обычный
        when(settingsService.isUserAdmin(anyLong(), anyLong())).thenReturn(false, true, false);

        service.processMessage(messageWithText(12, 777L, "текст"), plainUpdate());
        service.addWord("новое", 777L, -100L, 13);
        service.processMessage(messageWithText(14, 777L, "текст"), plainUpdate());

        verify(wordsFilterRepo, times(2)).findAll();
        verify(wordsFilterRepo).save(any(WordsFilter.class));
    }

    private MessageDTO messageWithText(Integer messageId, Long userId, String text) {
        return MessageDTO.builder()
                .chatId(-100L)
                .messageId(messageId)
                .userId(userId)
                .userText(text)
                .build();
    }

    private Update plainUpdate() {
        Update update = new Update();
        update.setMessage(new Message());
        return update;
    }

    private MessageDTO message(Long userId) {
        return MessageDTO.builder()
                .chatId(-100L)
                .messageId(5)
                .userId(userId)
                .userText("смотри https://example.com")
                .build();
    }

    private Update linkUpdate() {
        MessageEntity urlEntity = new MessageEntity();
        urlEntity.setType("url");
        urlEntity.setUrl("https://example.com");

        Message message = new Message();
        message.setEntities(List.of(urlEntity));

        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
