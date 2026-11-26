package ru.zhendozzz.tgbot.messageStatBot.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SummaryServiceTest {

    private static final String CHAT = "-100500";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);

    private final LogRepo logRepo = mock(LogRepo.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final ChatSettingsRepo chatSettingsRepo = mock(ChatSettingsRepo.class);
    private final MessageExecutor messageExecutor = mock(MessageExecutor.class);

    private SummaryService service() {
        SummaryService service = new SummaryService(logRepo, settingsService, chatSettingsRepo, messageExecutor);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(service, "model", "deepseek-chat");
        ReflectionTestUtils.setField(service, "maxInputChars", 20_000);
        ReflectionTestUtils.setField(service, "maxMessageChars", 300);
        ReflectionTestUtils.setField(service, "minMessages", 15);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 500);
        return service;
    }

    private MockRestServiceServer server(SummaryService service) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    private List<Log> messages(int count) {
        List<Log> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Log log = new Log();
            log.setChatId(CHAT);
            log.setUserId("42");
            log.setText("сообщение " + i);
            log.setDateTime(DAY.atTime(10, 0).plusMinutes(i));
            result.add(log);
        }
        return result;
    }

    @Test
    void doesNothingWithoutApiKey() {
        SummaryService service = new SummaryService(logRepo, settingsService, chatSettingsRepo, messageExecutor);
        ReflectionTestUtils.setField(service, "apiKey", "");

        assertThat(service.summarizePreviousDay()).isZero();
        assertThat(service.summarizeChat(CHAT, DAY)).isFalse();
        verify(logRepo, never()).findForSummary(anyString(), any(), any());
    }

    @Test
    void skipsDayWithTooFewMessages() {
        SummaryService service = service();
        when(logRepo.findForSummary(eq(CHAT), any(), any())).thenReturn(messages(5));

        assertThat(service.summarizeChat(CHAT, DAY)).isFalse();
        verify(messageExecutor, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void skipsWhenAlreadySummarized() {
        SummaryService service = service();
        ChatSettings done = new ChatSettings();
        done.setChatId(CHAT);
        done.setSettingName(SummaryService.LAST_DATE_SETTING);
        done.setSettingValue(DAY.toString());
        when(chatSettingsRepo.findFirstByChatIdAndSettingName(CHAT, SummaryService.LAST_DATE_SETTING))
                .thenReturn(done);

        assertThat(service.summarizeChat(CHAT, DAY)).isFalse();
        verify(logRepo, never()).findForSummary(anyString(), any(), any());
    }

    @Test
    void sendsSummaryAndRemembersDate() {
        SummaryService service = service();
        MockRestServiceServer server = server(service);
        when(logRepo.findForSummary(eq(CHAT), any(), any())).thenReturn(messages(20));
        when(messageExecutor.sendMessage(eq(CHAT), anyString())).thenReturn(10);
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("\"model\":\"deepseek-chat\"")))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"• Обсудили поездку\"}}],"
                        + "\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":80}}", MediaType.APPLICATION_JSON));

        assertThat(service.summarizeChat(CHAT, DAY)).isTrue();
        server.verify();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(messageExecutor).sendMessage(eq(CHAT), text.capture());
        assertThat(text.getValue()).contains("Саммари за 25.11.2026").contains("Обсудили поездку");

        ArgumentCaptor<ChatSettings> saved = ArgumentCaptor.forClass(ChatSettings.class);
        verify(chatSettingsRepo).save(saved.capture());
        assertThat(saved.getValue().getSettingName()).isEqualTo(SummaryService.LAST_DATE_SETTING);
        assertThat(saved.getValue().getSettingValue()).isEqualTo(DAY.toString());
    }

    @Test
    void doesNotMarkDateWhenApiFails() {
        SummaryService service = service();
        MockRestServiceServer server = server(service);
        when(logRepo.findForSummary(eq(CHAT), any(), any())).thenReturn(messages(20));
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":\"rate limit\"}"));

        assertThat(service.summarizeChat(CHAT, DAY)).isFalse();
        verify(messageExecutor, never()).sendMessage(anyString(), anyString());
        verify(chatSettingsRepo, never()).save(any(ChatSettings.class));
    }

    @Test
    void doesNotMarkDateWhenDeliveryFails() {
        SummaryService service = service();
        MockRestServiceServer server = server(service);
        when(logRepo.findForSummary(eq(CHAT), any(), any())).thenReturn(messages(20));
        when(messageExecutor.sendMessage(eq(CHAT), anyString())).thenReturn(null);
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"• Итог\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.summarizeChat(CHAT, DAY)).isFalse();
        verify(chatSettingsRepo, never()).save(any(ChatSettings.class));
    }

    @Test
    void doesNotSendWhenModelReturnsEmptyContent() {
        SummaryService service = service();
        MockRestServiceServer server = server(service);
        when(logRepo.findForSummary(eq(CHAT), any(), any())).thenReturn(messages(20));
        // Так отвечает reasoning-модель, когда max_tokens ушёл на размышления.
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"\"},"
                        + "\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":500,"
                        + "\"completion_tokens_details\":{\"reasoning_tokens\":500}}}", MediaType.APPLICATION_JSON));

        assertThat(service.summarizeChat(CHAT, DAY)).isFalse();
        verify(messageExecutor, never()).sendMessage(anyString(), anyString());
        verify(chatSettingsRepo, never()).save(any(ChatSettings.class));
    }

    @Test
    void dialogKeepsAuthorAndTruncatesLongMessages() {
        SummaryService service = service();
        when(logRepo.findNamesByIds(anyList())).thenReturn(List.of());
        Log longMessage = new Log();
        longMessage.setChatId(CHAT);
        longMessage.setUserId("42");
        longMessage.setText("я".repeat(500));
        longMessage.setDateTime(DAY.atTime(9, 0));
        Log shortMessage = new Log();
        shortMessage.setChatId(CHAT);
        shortMessage.setUserId("42");
        shortMessage.setText("короткое");
        shortMessage.setDateTime(DAY.atTime(9, 1));

        String dialog = service.buildDialog(List.of(longMessage, shortMessage));

        assertThat(dialog).contains("42: ").contains("короткое");
        assertThat(dialog.lines().findFirst().orElse("")).hasSizeLessThanOrEqualTo(305);
    }

    @Test
    void capLimitsDialogAndKeepsOrder() {
        SummaryService service = service();
        ReflectionTestUtils.setField(service, "maxInputChars", 100);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            lines.add("строка " + i + " " + "x".repeat(20));
        }

        String capped = service.capLines(lines);

        assertThat(capped.length()).isLessThanOrEqualTo(100);
    }
}
