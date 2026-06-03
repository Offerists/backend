package ru.hack.aiprojectmanager.kanban.yougile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskRequest;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class YougileMapper {

    private final ZoneId zone;

    public YougileMapper(@Value("${app.timezone}") String timezone) {
        this.zone = ZoneId.of(timezone);
    }

    public Task toDomain(YougileTaskDto dto, WorkspaceSettings settings) {
        List<String> assigneeIds = dto.assigned() != null
                ? dto.assigned().entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList()
                : List.of();

        return Task.builder()
                .externalId(dto.id())
                .title(dto.title())
                .description(dto.description())
                .columnId(dto.columnId())
                .status(columnToStatus(dto.columnId(), settings))
                .assigneeIds(assigneeIds)
                .assigneeId(assigneeIds.isEmpty() ? null : assigneeIds.getFirst())
                .deadline(dto.deadline() != null ? toLocalDateTime(dto.deadline()) : null)
                .startDate(dto.startDate() != null ? toLocalDateTime(dto.startDate()) : null)
                .build();
    }

    public YougileTaskRequest toRequest(Task task, WorkspaceSettings settings) {
        List<String> assigned = null;
        if (task.getAssigneeIds() != null && !task.getAssigneeIds().isEmpty()) {
            assigned = task.getAssigneeIds().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } else if (task.getAssigneeId() != null) {
            assigned = List.of(task.getAssigneeId());
        }

        return YougileTaskRequest.builder()
                .title(task.getTitle())
                .description(task.getDescription())
                .columnId(statusToColumn(task.getStatus(), settings))
                .deadline(task.getDeadline() != null ? toEpochMillis(task.getDeadline()) : null)
                .startDate(task.getStartDate() != null ? toEpochMillis(task.getStartDate()) : null)
                .assigned(assigned != null && assigned.isEmpty() ? null : assigned)
                .build();
    }

    public YougileTaskRequest toMoveRequest(TaskStatus newStatus, WorkspaceSettings settings) {
        return YougileTaskRequest.builder()
                .columnId(statusToColumn(newStatus, settings))
                .build();
    }

    private TaskStatus columnToStatus(String columnId, WorkspaceSettings s) {
        if (columnId == null) return TaskStatus.TODO;
        if (columnId.equals(s.getColumnTodoId())) return TaskStatus.TODO;
        if (columnId.equals(s.getColumnInProgressId())) return TaskStatus.IN_PROGRESS;
        if (columnId.equals(s.getColumnReviewId())) return TaskStatus.REVIEW;
        if (columnId.equals(s.getColumnDoneId())) return TaskStatus.DONE;
        return TaskStatus.TODO;
    }

    private String statusToColumn(TaskStatus status, WorkspaceSettings s) {
        return switch (status != null ? status : TaskStatus.TODO) {
            case TODO -> s.getColumnTodoId();
            case IN_PROGRESS -> s.getColumnInProgressId();
            case REVIEW -> s.getColumnReviewId();
            case DONE -> s.getColumnDoneId();
        };
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime();
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(zone).toInstant().toEpochMilli();
    }
}
