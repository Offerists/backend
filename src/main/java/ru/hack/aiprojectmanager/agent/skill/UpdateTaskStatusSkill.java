package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.Arrays;
import java.util.List;

@Component
public class UpdateTaskStatusSkill implements Skill {

    private static final List<String> STATUSES = Arrays.stream(TaskStatus.values())
            .map(Enum::name)
            .toList();

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("task_id", "string", "ID задачи из результата get_user_tasks")
            .required("status", "string", "Новый статус задачи")
            .enumValues("status", STATUSES)
            .build();

    private final KanbanProvider kanban;
    private final AppUserRepository appUserRepository;

    public UpdateTaskStatusSkill(KanbanProvider kanban, AppUserRepository appUserRepository) {
        this.kanban = kanban;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public String getName() {
        return "update_task_status";
    }

    @Override
    public String getDescription() {
        return "Переместить задачу в другой статус. task_id берётся из результата get_user_tasks.";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long telegramUserId, JsonNode args) {
        String taskId = requireText(args, "task_id");
        String rawStatus = requireText(args, "status");
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "неизвестный статус «" + rawStatus + "», допустимы: " + String.join(", ", STATUSES));
        }

        AppUser user = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (user != null && "MEMBER".equals(user.getYougileRole())) {
            Task task = kanban.getTask(telegramUserId, taskId);
            boolean isOwner = task != null && task.getAssigneeIds() != null
                    && task.getAssigneeIds().contains(user.getYougileUserId());
            if (!isOwner) {
                return "Вы можете изменять статус только своих задач.";
            }
        }

        kanban.moveTask(telegramUserId, taskId, newStatus);
        return "Статус обновлён";
    }
}
