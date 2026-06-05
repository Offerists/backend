package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.storage.TaskEntity;
import ru.hack.aiprojectmanager.storage.TaskEntityRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SetReminderSkill {

    private final TaskEntityRepository taskEntityRepository;

    @Tool(name = "set_reminder", description = "Установить напоминание по задаче на указанное время.")
    public String setReminder(
            @ToolParam(description = "task_id из find_task") String taskId,
            @ToolParam(description = "Время напоминания yyyy-MM-ddTHH:mm") String remindAt,
            @Nullable @ToolParam(description = "Название задачи", required = false) String title,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(remindAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return "Неверный формат времени. Используй yyyy-MM-ddTHH:mm";
        }

        TaskEntity task = taskEntityRepository.findByYougileTaskIdAndChatId(taskId, telegramUserId)
                .orElseGet(() -> TaskEntity.builder()
                        .yougileTaskId(taskId).chatId(telegramUserId)
                        .title(title != null ? title : taskId).build());

        task.setDeadline(time);
        task.setReminderSentAt(null);
        taskEntityRepository.save(task);

        return "Напоминание установлено на " + time.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }
}
