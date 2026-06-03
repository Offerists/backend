package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ListTasksSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("assignee_id", "string", "ID пользователя в YouGile")
            .build();

    private final KanbanProvider kanban;

    public ListTasksSkill(KanbanProvider kanban) {
        this.kanban = kanban;
    }

    @Override
    public String getName() {
        return "list_tasks";
    }

    @Override
    public String getDescription() {
        return "Получить список задач пользователя";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long chatId, JsonNode args) {
        String assigneeId = args.get("assignee_id").asText();
        List<Task> tasks = kanban.getTasksByAssignee(chatId, assigneeId);

        if (tasks.isEmpty()) {
            return "Задач не найдено";
        }

        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            sb.append("• [").append(t.getStatus()).append("] ")
                    .append(t.getTitle());
            if (t.getDeadline() != null) {
                sb.append(" (до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(")");
            }
            sb.append(" — id:").append(t.getExternalId()).append("\n");
        }
        return sb.toString().trim();
    }
}
