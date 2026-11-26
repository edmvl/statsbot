package ru.zhendozzz.tgbot.messageStatBot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import ru.zhendozzz.tgbot.messageStatBot.dto.UserNameDto;
import ru.zhendozzz.tgbot.messageStatBot.entity.ChatSettings;
import ru.zhendozzz.tgbot.messageStatBot.entity.Log;
import ru.zhendozzz.tgbot.messageStatBot.message_executor.MessageExecutor;
import ru.zhendozzz.tgbot.messageStatBot.repo.ChatSettingsRepo;
import ru.zhendozzz.tgbot.messageStatBot.repo.LogRepo;
import ru.zhendozzz.tgbot.messageStatBot.util.enums.ChatSettingConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ежедневное саммари переписки: собирает сообщения чата за прошедшие сутки,
 * просит DeepSeek сделать краткий пересказ и публикует его в чат.
 *
 * <p>Работает по настройке {@code enable_summary} конкретного чата. Ключ API —
 * отдельный ({@code summary.api-key}), чтобы расходы и отзыв доступа не задевали
 * остальные интеграции. Без ключа сервис молча выключен.</p>
 */
@Service
@Slf4j
public class SummaryService {

    /** Дата последнего отправленного саммари — защита от повторной отправки. */
    static final String LAST_DATE_SETTING = "summary_last_date";

    private static final int TELEGRAM_MESSAGE_LIMIT = 4000;
    private static final int MAX_POSTERS = 3;

    /** Цены DeepSeek Flash, $ за 1M токенов — только для оценки расхода в логе. */
    private static final double PRICE_INPUT = 0.30;
    private static final double PRICE_OUTPUT = 1.20;

    private static final String SYSTEM_PROMPT =
            "Ты делаешь краткое саммари переписки в Telegram-чате за день. "
                    + "Пиши по-русски, 5–8 пунктов, каждый пункт — одна короткая строка. "
                    + "Только факты из переписки: о чём договорились, что произошло, какие вопросы остались. "
                    + "Без вступлений, без выводов, без оценок, без упоминания, что это пересказ.";

    private final LogRepo logRepo;
    private final SettingsService settingsService;
    private final ChatSettingsRepo chatSettingsRepo;
    private final MessageExecutor messageExecutor;

    @Value("${summary.api-key:}")
    private String apiKey;

    @Value("${summary.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${summary.model:deepseek-flash}")
    private String model;

    @Value("${summary.max-input-chars:20000}")
    private int maxInputChars;

    @Value("${summary.max-message-chars:300}")
    private int maxMessageChars;

    @Value("${summary.min-messages:15}")
    private int minMessages;

    @Value("${summary.max-output-tokens:500}")
    private int maxOutputTokens;

    private RestTemplate restTemplate;

    public SummaryService(LogRepo logRepo,
                          SettingsService settingsService,
                          ChatSettingsRepo chatSettingsRepo,
                          MessageExecutor messageExecutor) {
        this.logRepo = logRepo;
        this.settingsService = settingsService;
        this.chatSettingsRepo = chatSettingsRepo;
        this.messageExecutor = messageExecutor;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Саммари за прошедший календарный день по всем чатам с включённой настройкой.
     * Запускается в 00:30, когда у DeepSeek действует ночная скидка.
     *
     * @return сколько саммари реально отправлено
     */
    public int summarizePreviousDay() {
        if (isDisabled()) {
            log.info("summary: ключ API не настроен — саммари не делаем");
            return 0;
        }
        LocalDate day = LocalDate.now().minusDays(1);
        int sent = 0;
        for (String chatId : settingsService.findEnabledBySettingName(ChatSettingConstants.ENABLE_SUMMARY)) {
            try {
                if (summarizeChat(chatId, day)) {
                    sent++;
                }
            } catch (Exception e) {
                // Один сломанный чат не должен ронять остальные и повторяться штормом.
                log.error("summary: чат {} за {} не обработан: {}", chatId, day, e.getMessage());
            }
        }
        log.info("summary: за {} отправлено {} саммари", day, sent);
        return sent;
    }

    /** Саммари одного чата за день. {@code true} — отправлено. */
    public boolean summarizeChat(String chatId, LocalDate day) {
        if (isDisabled() || alreadySummarized(chatId, day)) {
            return false;
        }
        LocalDateTime from = day.atStartOfDay();
        List<Log> messages = logRepo.findForSummary(chatId, from, from.plusDays(1));
        if (messages.size() < minMessages) {
            log.info("summary: чат {} за {} — {} сообщений, меньше минимума {}",
                    chatId, day, messages.size(), minMessages);
            return false;
        }
        String dialog = buildDialog(messages);
        String summary = askModel(chatId, day, dialog, messages.size());
        if (summary == null || summary.isBlank()) {
            return false;
        }
        String text = compose(day, summary, messages);
        Integer messageId = messageExecutor.sendMessage(chatId, text);
        if (Objects.isNull(messageId)) {
            log.warn("summary: чат {} — саммари не доставлено", chatId);
            return false;
        }
        markSummarized(chatId, day);
        return true;
    }

    /** Переписка в виде «автор: текст», с обрезкой длинных сообщений и общего объёма. */
    String buildDialog(List<Log> messages) {
        Set<String> userIds = messages.stream()
                .map(Log::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserNameDto user : logRepo.findNamesByIds(userIds)) {
                names.put(user.getId(), user.getName());
            }
        }
        List<String> lines = new ArrayList<>();
        for (Log message : messages) {
            String text = Objects.toString(message.getText(), "").replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > maxMessageChars) {
                text = text.substring(0, maxMessageChars) + "…";
            }
            String author = names.getOrDefault(message.getUserId(),
                    Objects.isNull(message.getUserId()) ? "кто-то" : message.getUserId());
            lines.add(author + ": " + text);
        }
        return capLines(lines);
    }

    /** Если переписка не влезает в бюджет символов — берём равномерную выборку. */
    String capLines(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        long total = lines.stream().mapToLong(line -> line.length() + 1L).sum();
        if (total <= maxInputChars) {
            return String.join("\n", lines);
        }
        int keep = Math.max(1, (int) Math.floor(lines.size() * (double) maxInputChars / total));
        double step = (double) lines.size() / keep;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keep; i++) {
            int index = Math.min(lines.size() - 1, (int) Math.floor(i * step));
            builder.append(lines.get(index)).append('\n');
        }
        String result = builder.toString();
        return result.length() > maxInputChars ? result.substring(0, maxInputChars) : result;
    }

    private String askModel(String chatId, LocalDate day, String dialog, int messageCount) {
        String userPrompt = "Дата: " + day + ". Сообщений: " + messageCount + ".\n"
                + "Переписка (каждая строка — «автор: сообщение»):\n" + dialog;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)));
        payload.put("temperature", 0.3);
        payload.put("max_tokens", maxOutputTokens);
        payload.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/chat/completions", new HttpEntity<>(payload, headers), Map.class);
            String content = extractContent(response);
            logUsage(chatId, response);
            return content;
        } catch (HttpStatusCodeException e) {
            // 401/402/429 — ключ, баланс или лимит: повторять бессмысленно, только сообщаем.
            log.warn("summary: чат {} — DeepSeek ответил {}: {}",
                    chatId, e.getRawStatusCode(), shortBody(e.getResponseBodyAsString()));
            return null;
        } catch (Exception e) {
            log.warn("summary: чат {} — ошибка запроса к DeepSeek: {}", chatId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (Objects.isNull(response)) {
            return null;
        }
        Object choices = response.get("choices");
        if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
            return null;
        }
        Object first = ((List<?>) choices).get(0);
        if (!(first instanceof Map)) {
            return null;
        }
        Object message = ((Map<String, Object>) first).get("message");
        if (!(message instanceof Map)) {
            return null;
        }
        Object content = ((Map<String, Object>) message).get("content");
        return Objects.isNull(content) ? null : content.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void logUsage(String chatId, Map<String, Object> response) {
        if (Objects.isNull(response) || !(response.get("usage") instanceof Map)) {
            return;
        }
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        long input = asLong(usage.get("prompt_tokens"));
        long output = asLong(usage.get("completion_tokens"));
        double cost = (input * PRICE_INPUT + output * PRICE_OUTPUT) / 1_000_000d;
        log.info("summary: чат {} — токены in={} out={}, примерно ${}", chatId, input, output,
                String.format("%.5f", cost));
    }

    private long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String compose(LocalDate day, String summary, List<Log> messages) {
        String header = "📊 Саммари за " + day.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String posters = topPosters(messages);
        String text = header + "\n" + summary.trim() + (posters.isEmpty() ? "" : "\n\n" + posters);
        return text.length() > TELEGRAM_MESSAGE_LIMIT
                ? text.substring(0, TELEGRAM_MESSAGE_LIMIT - 1) + "…"
                : text;
    }

    /** Кто больше всех писал — короткой строкой, без отдельного запроса к базе. */
    private String topPosters(List<Log> messages) {
        Map<String, Long> counts = messages.stream()
                .map(Log::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        if (counts.isEmpty()) {
            return "";
        }
        List<String> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_POSTERS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Map<String, String> names = new HashMap<>();
        for (UserNameDto user : logRepo.findNamesByIds(top)) {
            names.put(user.getId(), user.getName());
        }
        String list = top.stream()
                .map(id -> names.getOrDefault(id, id) + " (" + counts.get(id) + ")")
                .collect(Collectors.joining(", "));
        return "Активнее всех: " + list;
    }

    private boolean isDisabled() {
        return Objects.isNull(apiKey) || apiKey.isBlank();
    }

    private boolean alreadySummarized(String chatId, LocalDate day) {
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, LAST_DATE_SETTING);
        return Objects.nonNull(setting) && day.toString().equals(setting.getSettingValue());
    }

    private void markSummarized(String chatId, LocalDate day) {
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, LAST_DATE_SETTING);
        if (Objects.isNull(setting)) {
            setting = new ChatSettings();
            setting.setChatId(chatId);
            setting.setSettingName(LAST_DATE_SETTING);
        }
        setting.setSettingValue(day.toString());
        chatSettingsRepo.save(setting);
    }

    /** Дата последнего саммари для страницы настроек чата. */
    public String lastSummaryDate(String chatId) {
        ChatSettings setting = chatSettingsRepo.findFirstByChatIdAndSettingName(chatId, LAST_DATE_SETTING);
        return Objects.isNull(setting) ? null : setting.getSettingValue();
    }

    private String shortBody(String body) {
        if (Objects.isNull(body)) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
