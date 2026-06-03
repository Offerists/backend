package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
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
            .required("task_id", "string", "Внешний ID задачи")
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
        return "Переместить задачу в другой статус (колонку)";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long chatId, JsonNode args) {
        String taskId = args.get("task_id").asText();
        TaskStatus newStatus = TaskStatus.valueOf(args.get("status").asText());
        kanban.moveTask(chatId, taskId, newStatus);
        return "Статус задачи " + taskId + " обновлён: " + newStatus;
    }
}
