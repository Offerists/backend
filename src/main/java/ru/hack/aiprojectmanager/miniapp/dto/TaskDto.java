package ru.hack.aiprojectmanager.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Schema(description = "Задача")
public record TaskDto(
        @Schema(description = "ID задачи в YouGile", example = "abc123def456") String id,
        @Schema(description = "Название задачи", example = "Доработать UI кнопок") String title,
        @Schema(description = "Описание задачи", example = "Правки по макету v2") String description,
        @Schema(description = "Статус", allowableValues = {"TODO", "IN_PROGRESS", "REVIEW", "DONE"}, example = "IN_PROGRESS") String status,
        @Schema(description = "Читаемый статус на русском", example = "В работе") String statusLabel,
        @Schema(description = "Дедлайн в ISO 8601", example = "2025-07-01T00:00:00") String deadline,
        @Schema(description = "Исполнители задачи") List<AssigneeDto> assignees
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static TaskDto from(Task task, Map<String, String> userNames) {
        List<AssigneeDto> assignees = task.getAssigneeIds() == null ? List.of() :
                task.getAssigneeIds().stream()
                        .map(id -> new AssigneeDto(id, userNames.getOrDefault(id, id)))
                        .toList();
        return new TaskDto(
                task.getExternalId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus() != null ? task.getStatus().name() : TaskStatus.TODO.name(),
                statusLabel(task.getStatus()),
                task.getDeadline() != null ? task.getDeadline().format(FMT) : null,
                assignees
        );
    }

    /** Convenience overload when user names are not available. */
    public static TaskDto from(Task task) {
        return from(task, Map.of());
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
