package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class AssignTaskSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("task_id", "string", "ID задачи из find_task")
            .required("assignee", "string", "Имя, @username или telegram_id исполнителя")
            .build();

    private final KanbanProvider kanban;
    private final AppUserRepository appUserRepository;

    public AssignTaskSkill(KanbanProvider kanban, AppUserRepository appUserRepository) {
        this.kanban = kanban;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public String getName() {
        return "assign_task";
    }

    @Override
    public String getDescription() {
        return "Назначить исполнителя на существующую задачу. task_id берётся из find_task. "
                + "assignee принимает имя, @username или числовой telegram_id.";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long telegramUserId, JsonNode args) {
        String taskId = requireText(args, "task_id");
        String assigneeQuery = requireText(args, "assignee");

        String yougileUserId = resolveYougileUserId(telegramUserId, assigneeQuery);
        if (yougileUserId == null) {
            return "Пользователь «" + assigneeQuery + "» не найден или не привязан к YouGile.";
        }

        Task current = kanban.getTask(telegramUserId, taskId);
        if (current == null) {
            return "Задача не найдена.";
        }

        Task updated = Task.builder()
                .title(current.getTitle())
                .description(current.getDescription())
                .status(current.getStatus())
                .columnId(current.getColumnId())
                .deadline(current.getDeadline())
                .assigneeId(yougileUserId)
                .assigneeIds(List.of(yougileUserId))
                .build();

        kanban.updateTask(telegramUserId, taskId, updated);

        AppUser assignee = appUserRepository.findFirstByTelegramId(
                resolveNumericId(assigneeQuery, telegramUserId)).orElse(null);
        String displayName = assignee != null && assignee.getFullName() != null
                ? assignee.getFullName() : assigneeQuery;

        return "Задача «" + current.getTitle() + "» назначена на " + displayName + ".";
    }

    private String resolveYougileUserId(Long telegramUserId, String query) {
        // Числовой telegram_id
        try {
            long id = Long.parseLong(query);
            return appUserRepository.findFirstByTelegramId(id)
                    .map(AppUser::getYougileUserId)
                    .orElse(null);
        } catch (NumberFormatException ignored) {}

        // Username или имя
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null) return null;

        String q = query.startsWith("@") ? query.substring(1).toLowerCase() : query.toLowerCase();
        List<AppUser> candidates = requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                : List.of();

        return candidates.stream()
                .filter(u -> matchesQuery(u, q))
                .map(AppUser::getYougileUserId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    private Long resolveNumericId(String query, Long fallback) {
        try {
            return Long.parseLong(query);
        } catch (NumberFormatException ignored) {}
        AppUser requester = appUserRepository.findFirstByTelegramId(fallback).orElse(null);
        if (requester == null) return fallback;
        String q = query.startsWith("@") ? query.substring(1).toLowerCase() : query.toLowerCase();
        List<AppUser> candidates = requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                : List.of();
        return candidates.stream()
                .filter(u -> matchesQuery(u, q))
                .map(AppUser::getTelegramId)
                .findFirst()
                .orElse(fallback);
    }

    private boolean matchesQuery(AppUser u, String q) {
        return (u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(q))
                || (u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(q));
    }
}
