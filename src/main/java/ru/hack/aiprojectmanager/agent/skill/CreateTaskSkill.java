package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

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
    public String execute(Long chatId, JsonNode args) {
        Task task = Task.builder()
                .title(args.get("title").asText())
                .description(textOrNull(args, "description"))
                .assigneeId(resolveAssignee(chatId, args))
                .status(TaskStatus.TODO)
                .deadline(parseDeadline(args))
                .build();

        kanban.createTask(chatId, task);
        return "Задача «" + task.getTitle() + "» создана";
    }

    private String resolveAssignee(Long chatId, JsonNode args) {
        String telegramIdStr = textOrNull(args, "assignee_telegram_id");
        if (telegramIdStr == null) return null;
        try {
            long telegramId = Long.parseLong(telegramIdStr);
            return appUserRepository.findByTelegramIdAndChatId(telegramId, chatId)
                    .map(u -> u.getYougileUserId())
                    .orElse(null);
        } catch (NumberFormatException e) {
            log.warn("Invalid assignee_telegram_id '{}', skipping", telegramIdStr);
            return null;
        }
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
