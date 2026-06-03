package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.List;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class CreateTaskSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("title", "string", "Название задачи")
            .optional("description", "string", "Описание задачи")
            .optional("assignee_telegram_id", "string", "Telegram ID исполнителя")
            .optional("deadline", "string", "Дедлайн в формате ISO-8601 (yyyy-MM-ddTHH:mm)")
            .build();

    private final KanbanProvider kanban;
    private final AppUserRepository appUserRepository;

    public CreateTaskSkill(KanbanProvider kanban, AppUserRepository appUserRepository) {
        this.kanban = kanban;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public String getName() {
        return "create_task";
    }

    @Override
    public String getDescription() {
        return "Создать новую задачу на канбан-доске";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long telegramUserId, JsonNode args) {
        Task task = Task.builder()
                .title(requireText(args, "title"))
                .description(textOrNull(args, "description"))
                .assigneeId(resolveAssignee(telegramUserId, args))
                .status(TaskStatus.TODO)
                .deadline(parseDeadline(args))
                .build();

        kanban.createTask(telegramUserId, task);
        return "Задача «" + task.getTitle() + "» создана";
    }

    private String resolveAssignee(Long telegramUserId, JsonNode args) {
        String raw = textOrNull(args, "assignee_telegram_id");
        if (raw == null) return null;

        // Числовой telegramId
        try {
            long telegramId = Long.parseLong(raw);
            return appUserRepository.findFirstByTelegramId(telegramId)
                    .map(AppUser::getYougileUserId)
                    .orElse(null);
        } catch (NumberFormatException ignored) {}

        // Username (@smurphi или smurphi) или имя — ищем по всем известным пользователям компании
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null) return null;

        String query = raw.startsWith("@") ? raw.substring(1).toLowerCase() : raw.toLowerCase();
        List<AppUser> candidates = requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                : List.of();

        return candidates.stream()
                .filter(u -> matchesQuery(u, query))
                .map(AppUser::getYougileUserId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    private boolean matchesQuery(AppUser u, String query) {
        return (u.getUsername() != null && u.getUsername().toLowerCase().contains(query))
                || (u.getFullName() != null && u.getFullName().toLowerCase().contains(query));
    }

    private String textOrNull(JsonNode args, String field) {
        JsonNode node = args.get(field);
        return (node != null && !node.isNull() && !node.asText().isBlank()) ? node.asText() : null;
    }

    private LocalDateTime parseDeadline(JsonNode args) {
        String raw = textOrNull(args, "deadline");
        if (raw == null) return null;
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("Could not parse deadline '{}', ignoring", raw);
            return null;
        }
    }
}
