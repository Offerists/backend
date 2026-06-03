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
    public String execute(Long chatId, JsonNode args) {
        String taskId = args.get("task_id").asText();
        TaskStatus newStatus = TaskStatus.valueOf(args.get("status").asText());
        kanban.moveTask(chatId, taskId, newStatus);
        return "Статус обновлён";
    }
}
