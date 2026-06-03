package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;

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

    public UpdateTaskStatusSkill(KanbanProvider kanban) {
        this.kanban = kanban;
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
        kanban.moveTask(telegramUserId, taskId, newStatus);
        return "Статус обновлён";
    }
}
