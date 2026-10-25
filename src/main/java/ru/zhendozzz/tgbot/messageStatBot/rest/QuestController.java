package ru.zhendozzz.tgbot.messageStatBot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.zhendozzz.tgbot.messageStatBot.config.AdminAuthGuard;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestParticipantView;
import ru.zhendozzz.tgbot.messageStatBot.dto.QuestProgress;
import ru.zhendozzz.tgbot.messageStatBot.entity.Quest;
import ru.zhendozzz.tgbot.messageStatBot.entity.QuestCheckpoint;
import ru.zhendozzz.tgbot.messageStatBot.service.CurrentUserService;
import ru.zhendozzz.tgbot.messageStatBot.service.QuestService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Квесты в Mini App.
 *
 * <p>Участвовать может любой авторизованный пользователь; создавать квесты, чекпоинты
 * и смотреть результаты — только супер-админ. Права проверяются здесь, на сервере.</p>
 */
@Controller
@RequestMapping("/app/quests")
public class QuestController {

    private final QuestService questService;
    private final CurrentUserService currentUserService;
    private final AdminAuthGuard adminAuthGuard;

    public QuestController(
            QuestService questService,
            CurrentUserService currentUserService,
            AdminAuthGuard adminAuthGuard
    ) {
        this.questService = questService;
        this.currentUserService = currentUserService;
        this.adminAuthGuard = adminAuthGuard;
    }

    /** Список квестов: участнику — активные, супер-админу — все. */
    @GetMapping
    public String quests(HttpServletRequest request, HttpServletResponse response, Model model) throws IOException {
        String userId = requireUser(request, response);
        if (userId == null) {
            return null;
        }
        boolean superAdmin = currentUserService.isSuperAdmin(request);
        List<Quest> quests = superAdmin ? questService.findAll() : questService.findActive();

        Map<Long, Long> checkpointCounts = new HashMap<>();
        Map<Long, QuestProgress> progress = new HashMap<>();
        for (Quest quest : quests) {
            checkpointCounts.put(quest.getId(), questService.countCheckpoints(quest.getId()));
            progress.put(quest.getId(), questService.progress(quest.getId(), userId));
        }

        model.addAttribute("quests", quests);
        model.addAttribute("checkpointCounts", checkpointCounts);
        model.addAttribute("progress", progress);
        model.addAttribute("superAdmin", superAdmin);
        return "quests";
    }

    /** Страница квеста: карта, чекпоинты и форма ответа. */
    @GetMapping("/{id}")
    public String quest(
            @PathVariable("id") Long questId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) throws IOException {
        String userId = requireUser(request, response);
        if (userId == null) {
            return null;
        }
        Quest quest = questService.findByIdOrNull(questId);
        boolean superAdmin = currentUserService.isSuperAdmin(request);
        if (Objects.isNull(quest) || (!Boolean.TRUE.equals(quest.getActive()) && !superAdmin)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Квест не найден");
            return null;
        }
        List<QuestCheckpoint> checkpoints = questService.findCheckpoints(questId);
        model.addAttribute("quest", quest);
        model.addAttribute("checkpoints", checkpoints);
        model.addAttribute("progress", questService.progress(questId, userId));
        model.addAttribute("superAdmin", superAdmin);
        model.addAttribute("mediaDirs", checkpoints.stream()
                .collect(Collectors.toMap(QuestCheckpoint::getId, cp -> QuestService.mediaDir(cp.getId()))));
        return "quest";
    }

    @PostMapping("/{id}/join")
    public String join(
            @PathVariable("id") Long questId,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        String userId = requireUser(request, response);
        if (userId == null) {
            return null;
        }
        try {
            questService.join(questId, userId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("questError", e.getMessage());
        }
        return "redirect:/app/quests/" + questId;
    }

    @PostMapping("/{id}/answer")
    public String answer(
            @PathVariable("id") Long questId,
            @RequestParam("checkpointId") Long checkpointId,
            @RequestParam(value = "answer", required = false) String answer,
            @RequestParam(value = "lat", required = false) String lat,
            @RequestParam(value = "lon", required = false) String lon,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        String userId = requireUser(request, response);
        if (userId == null) {
            return null;
        }
        QuestService.AnswerResult result = questService.answer(
                questId, userId, checkpointId, answer, parseDouble(lat), parseDouble(lon));
        redirectAttributes.addFlashAttribute("answerResult", result.name());
        redirectAttributes.addFlashAttribute("answerCheckpoint", checkpointId);
        return "redirect:/app/quests/" + questId;
    }

    /** Редактор квеста (только супер-админ). */
    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable("id") Long questId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) throws IOException {
        if (!adminAuthGuard.allow(request, response, false)) {
            return null;
        }
        Quest quest = questService.findByIdOrNull(questId);
        if (Objects.isNull(quest)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Квест не найден");
            return null;
        }
        model.addAttribute("quest", quest);
        List<QuestCheckpoint> checkpoints = questService.findCheckpoints(questId);
        model.addAttribute("checkpoints", checkpoints);
        model.addAttribute("defaultRadius", QuestService.DEFAULT_RADIUS_M);
        model.addAttribute("maxRadius", QuestService.MAX_RADIUS_M);
        model.addAttribute("mediaDirs", checkpoints.stream()
                .collect(Collectors.toMap(QuestCheckpoint::getId, cp -> QuestService.mediaDir(cp.getId()))));
        return "quest-edit";
    }

    /** Создание квеста (только супер-админ). */
    @PostMapping("/new")
    public String create(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        if (!adminAuthGuard.allow(request, response, false)) {
            return null;
        }
        String adminId = currentUserService.idAsString(request);
        try {
            Quest quest = questService.create(title, description, adminId);
            return "redirect:/app/quests/" + quest.getId() + "/edit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("questError", e.getMessage());
            return "redirect:/app/quests";
        }
    }

    @PostMapping("/{id}/save")
    public String save(
            @PathVariable("id") Long questId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "active", required = false) String active,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        if (!adminAuthGuard.allow(request, response, false)) {
            return null;
        }
        try {
            questService.update(questId, title, description, Objects.nonNull(active));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("questError", e.getMessage());
        }
        return "redirect:/app/quests/" + questId + "/edit";
    }

    /** Добавление чекпоинта: координаты, радиус появления, вопрос и вложение. */
    @PostMapping("/{id}/checkpoints")
    public String addCheckpoint(
            @PathVariable("id") Long questId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("question") String question,
            @RequestParam("answer") String answer,
            @RequestParam("lat") String lat,
            @RequestParam("lon") String lon,
            @RequestParam(value = "radius", required = false) String radius,
            @RequestParam(value = "media", required = false) MultipartFile media,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        if (!adminAuthGuard.allow(request, response, false)) {
            return null;
        }
        try {
            questService.addCheckpoint(questId, title, question, answer, lat, lon, radius, media);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("questError", e.getMessage());
        }
        return "redirect:/app/quests/" + questId + "/edit";
    }

    @PostMapping("/{id}/checkpoints/{checkpointId}/delete")
    public String deleteCheckpoint(
            @PathVariable("id") Long questId,
            @PathVariable("checkpointId") Long checkpointId,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) throws IOException {
        if (!adminAuthGuard.allow(request, response, false)) {
            return null;
        }
        try {
            questService.deleteCheckpoint(questId, checkpointId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("questError", e.getMessage());
        }
        return "redirect:/app/quests/" + questId + "/edit";
    }

    /** Результаты: вкладки «Участники» и «Завершившие» (только супер-админ). */
    @GetMapping("/{id}/results")
    public String results(
            @PathVariable("id") Long questId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) throws IOException {
        if (!adminAuthGuard.allow(request, response, false)) {
            return null;
        }
        Quest quest = questService.findByIdOrNull(questId);
        if (Objects.isNull(quest)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Квест не найден");
            return null;
        }
        List<QuestParticipantView> all = questService.participantViews(questId);
        List<QuestParticipantView> completed = all.stream()
                .filter(view -> Objects.nonNull(view.getFinishedAt()))
                .toList();
        List<QuestParticipantView> participants = all.stream()
                .filter(view -> Objects.isNull(view.getFinishedAt()))
                .toList();

        model.addAttribute("quest", quest);
        model.addAttribute("participants", participants);
        model.addAttribute("completed", completed);
        model.addAttribute("totalStarted", all.size());
        model.addAttribute("checkpointCount", questService.countCheckpoints(questId));
        return "quest-results";
    }

    /**
     * Любой авторизованный пользователь. Если {@code initData} ещё нет — отдаём страницу,
     * которая положит cookie и перезагрузит запрос (как делает {@link AdminAuthGuard}).
     */
    private String requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userId = currentUserService.idAsString(request);
        if (Objects.isNull(userId)) {
            AdminAuthGuard.writeBootstrap(response);
        }
        return userId;
    }

    private Double parseDouble(String value) {
        if (Objects.isNull(value) || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
