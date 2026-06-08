package ru.hack.aiprojectmanager.miniapp.dto;

import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record TaskDto(
        String id,
        String title,
        String description,
        String status,
        String statusLabel,
        String deadline,
        List<String> assigneeIds
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static TaskDto from(Task task) {
        return new TaskDto(
                task.getExternalId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus() != null ? task.getStatus().name() : TaskStatus.TODO.name(),
                statusLabel(task.getStatus()),
                task.getDeadline() != null ? task.getDeadline().format(FMT) : null,
                task.getAssigneeIds() != null ? task.getAssigneeIds() : List.of()
        );
    }

    private static String statusLabel(TaskStatus status) {
        if (status == null) return "К выполнению";
        return switch (status) {
            case TODO -> "К выполнению";
            case IN_PROGRESS -> "В работе";
            case REVIEW -> "На проверке";
            case DONE -> "Готово";
        };
    }
}
