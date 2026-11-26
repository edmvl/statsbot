package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
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

    @Test
    void deletesForeignBotMentionAndMutesAuthor() {
        when(settingsService.isEnabled(anyString(), eq(ChatSettingConstants.ENABLE_BOT_MENTION_FILTER))).thenReturn(true);

        Message message = new Message();
        message.setFrom(user(777L, false));
        message.setText("Закажи у @spam_bot");

        service.deleteByFilter(messageWithText(20, 777L, "Закажи у @spam_bot"), updateWith(message));

        verify(messageExecutor).deleteMessage(-100L, 20);
        verify(logService).markDeleted(-100L, 20);
        verify(messageExecutor).restrictChatMember(-100L, 777L);
    }

    @Test
    void keepsMentionOfOwnBot() {
        ReflectionTestUtils.setField(service, "botUsername", "my_bot");
        when(settingsService.isEnabled(anyString(), eq(ChatSettingConstants.ENABLE_BOT_MENTION_FILTER))).thenReturn(true);

        Message message = new Message();
        message.setFrom(user(777L, false));
        message.setText("@my_bot покажи статистику");

        service.deleteByFilter(messageWithText(21, 777L, "@my_bot покажи статистику"), updateWith(message));

        verify(messageExecutor, never()).deleteMessage(any(), any());
        verify(messageExecutor, never()).restrictChatMember(any(), any());
    }

    @Test
    void deletesTextMentionOfForeignBot() {
        when(settingsService.isEnabled(anyString(), eq(ChatSettingConstants.ENABLE_BOT_MENTION_FILTER))).thenReturn(true);

        MessageEntity textMention = new MessageEntity();
        textMention.setType("text_mention");
        textMention.setOffset(0);
        textMention.setLength(9);
        textMention.setUser(user(999L, true));

        Message message = new Message();
        message.setFrom(user(777L, false));
        message.setText("Чужой бот");
        message.setEntities(List.of(textMention));

        service.deleteByFilter(messageWithText(22, 777L, "Чужой бот"), updateWith(message));

        verify(messageExecutor).deleteMessage(-100L, 22);
        verify(messageExecutor).restrictChatMember(-100L, 777L);
    }

    @Test
    void deletesLinkHiddenUnderInlineButton() {
        when(settingsService.isEnabled(anyString(), eq(ChatSettingConstants.ENABLE_BOT_MENTION_FILTER))).thenReturn(true);

        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("Открыть");
        button.setUrl("https://spam.example");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(button)));

        Message message = new Message();
        message.setFrom(user(777L, false));
        message.setText("Смотри");
        message.setReplyMarkup(markup);

        service.deleteByFilter(messageWithText(23, 777L, "Смотри"), updateWith(message));

        verify(messageExecutor).deleteMessage(-100L, 23);
        verify(messageExecutor).restrictChatMember(-100L, 777L);
    }

    @Test
    void keepsBotMentionsWhenSettingIsOff() {
        Message message = new Message();
        message.setFrom(user(777L, false));
        message.setText("Закажи у @spam_bot");

        service.deleteByFilter(messageWithText(24, 777L, "Закажи у @spam_bot"), updateWith(message));

        verify(messageExecutor, never()).deleteMessage(any(), any());
        verify(messageExecutor, never()).restrictChatMember(any(), any());
    }

    @Test
    void deletesBotMessageButDoesNotTryToRestrictBot() {
        when(settingsService.isEnabled(anyString(), eq(ChatSettingConstants.ENABLE_BOT_MENTION_FILTER))).thenReturn(true);

        Message message = new Message();
        message.setFrom(user(555L, true));
        message.setText("Закажи у @spam_bot");

        service.deleteByFilter(messageWithText(25, 555L, "Закажи у @spam_bot"), updateWith(message));

        verify(messageExecutor).deleteMessage(-100L, 25);
        verify(messageExecutor, never()).restrictChatMember(any(), any());
    }

    private User user(Long id, boolean isBot) {
        User user = new User();
        user.setId(id);
        user.setIsBot(isBot);
        return user;
    }

    private Update updateWith(Message message) {
        Update update = new Update();
        update.setMessage(message);
        return update;
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
