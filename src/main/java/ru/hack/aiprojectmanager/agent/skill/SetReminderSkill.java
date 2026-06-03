package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.storage.TaskEntity;
import ru.hack.aiprojectmanager.storage.TaskEntityRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class SetReminderSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("task_id", "string", "ID задачи в YouGile")
            .required("remind_at", "string", "Время напоминания в формате ISO-8601 (yyyy-MM-ddTHH:mm)")
            .optional("title", "string", "Название задачи")
            .build();

    private final TaskEntityRepository taskEntityRepository;

    public SetReminderSkill(TaskEntityRepository taskEntityRepository) {
        this.taskEntityRepository = taskEntityRepository;
    }

    @Override
    public String getName() {
        return "set_reminder";
    }

    @Override
    public String getDescription() {
        return "Установить напоминание по задаче на указанное время";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long chatId, JsonNode args) {
        String taskId = requireText(args, "task_id");
        String rawRemindAt = requireText(args, "remind_at");
        LocalDateTime remindAt;
        try {
            remindAt = LocalDateTime.parse(rawRemindAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "неверный формат времени «" + rawRemindAt + "», ожидается yyyy-MM-ddTHH:mm");
        }
        String title = optText(args, "title") != null ? optText(args, "title") : taskId;

        TaskEntity task = taskEntityRepository.findByYougileTaskIdAndChatId(taskId, chatId)
                .orElseGet(() -> TaskEntity.builder()
                        .yougileTaskId(taskId)
                        .chatId(chatId)
                        .title(title)
                        .build());

        task.setDeadline(remindAt);
        task.setReminderSentAt(null);
        taskEntityRepository.save(task);

        return "Напоминание установлено на " + remindAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }
}
