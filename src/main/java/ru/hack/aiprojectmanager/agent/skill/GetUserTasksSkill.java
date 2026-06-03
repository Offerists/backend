package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class GetUserTasksSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("telegram_user_id", "string", "Telegram ID пользователя чьи задачи нужно получить")
            .build();

    private final AppUserRepository appUserRepository;
    private final KanbanProvider kanban;

    public GetUserTasksSkill(AppUserRepository appUserRepository, KanbanProvider kanban) {
        this.appUserRepository = appUserRepository;
        this.kanban = kanban;
    }

    @Override
    public String getName() {
        return "get_user_tasks";
    }

    @Override
    public String getDescription() {
        return "Получить задачи пользователя по его Telegram ID";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long chatId, JsonNode args) {
        long telegramUserId = args.get("telegram_user_id").asLong();

        AppUser user = appUserRepository.findByTelegramIdAndChatId(telegramUserId, chatId)
                .orElse(null);

        if (user == null || user.getYougileUserId() == null) {
            return "Задачи не найдены";
        }

        List<Task> tasks = kanban.getTasksByAssignee(chatId, user.getYougileUserId());

        if (tasks.isEmpty()) {
            return "Активных задач нет";
        }

        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            sb.append("• «").append(t.getTitle()).append("» — ").append(statusLabel(t.getStatus().name()));
            if (t.getDeadline() != null) {
                sb.append(", до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            }
            // ID нужен только для инструментов, не показывай его пользователю
            sb.append(" [task_id:").append(t.getExternalId()).append("]");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "TODO" -> "К выполнению";
            case "IN_PROGRESS" -> "В работе";
            case "REVIEW" -> "На проверке";
            case "DONE" -> "Готово";
            default -> status;
        };
    }
}
