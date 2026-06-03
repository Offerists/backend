package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class CreateTaskSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("title", "string", "Название задачи")
            .optional("description", "string", "Описание задачи")
            .optional("assignee_id", "string", "ID исполнителя в YouGile")
            .optional("deadline", "string", "Дедлайн в формате ISO-8601 (yyyy-MM-ddTHH:mm)")
            .build();

    private final KanbanProvider kanban;

    public CreateTaskSkill(KanbanProvider kanban) {
        this.kanban = kanban;
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
    public String execute(Long chatId, JsonNode args) {
        Task task = Task.builder()
                .title(args.get("title").asText())
                .description(textOrNull(args, "description"))
                .assigneeId(textOrNull(args, "assignee_id"))
                .status(TaskStatus.TODO)
                .deadline(parseDeadline(args))
                .build();

        String externalId = kanban.createTask(chatId, task);
        return "Задача создана, id: " + externalId;
    }

    private String textOrNull(JsonNode args, String field) {
        JsonNode node = args.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private LocalDateTime parseDeadline(JsonNode args) {
        String raw = textOrNull(args, "deadline");
        if (raw == null) return null;
        return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
