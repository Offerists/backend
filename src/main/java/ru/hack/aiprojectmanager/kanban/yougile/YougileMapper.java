package ru.hack.aiprojectmanager.kanban.yougile;

import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskRequest;
import ru.hack.aiprojectmanager.storage.UserBoardSettings;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class YougileMapper {

    public Task toDomain(YougileTaskDto dto, UserBoardSettings board) {
        List<String> assigneeIds = dto.assigned() != null ? dto.assigned() : List.of();

        return Task.builder()
                .externalId(dto.id())
                .title(dto.title())
                .description(dto.description())
                .columnId(dto.columnId())
                .status(columnToStatus(dto.columnId(), board))
                .assigneeIds(assigneeIds)
                .assigneeId(assigneeIds.isEmpty() ? null : assigneeIds.getFirst())
                .deadline(dto.deadline() != null ? toLocalDateTime(dto.deadline()) : null)
                .startDate(dto.startDate() != null ? toLocalDateTime(dto.startDate()) : null)
                .build();
    }

    public YougileTaskRequest toRequest(Task task, UserBoardSettings board) {
        List<String> assigned = new ArrayList<>();
        if (task.getAssigneeIds() != null) {
            assigned.addAll(task.getAssigneeIds());
        } else if (task.getAssigneeId() != null) {
            assigned.add(task.getAssigneeId());
        }

        return YougileTaskRequest.builder()
                .title(task.getTitle())
                .description(task.getDescription())
                .columnId(statusToColumn(task.getStatus(), board))
                .deadline(task.getDeadline() != null ? toEpochMillis(task.getDeadline()) : null)
                .startDate(task.getStartDate() != null ? toEpochMillis(task.getStartDate()) : null)
                .assigned(assigned.isEmpty() ? null : assigned)
                .build();
    }

    public YougileTaskRequest toMoveRequest(TaskStatus newStatus, UserBoardSettings board) {
        return YougileTaskRequest.builder()
                .columnId(statusToColumn(newStatus, board))
                .build();
    }

    private TaskStatus columnToStatus(String columnId, UserBoardSettings b) {
        if (columnId == null || b == null) return TaskStatus.TODO;
        if (columnId.equals(b.getColumnTodoId())) return TaskStatus.TODO;
        if (columnId.equals(b.getColumnInProgressId())) return TaskStatus.IN_PROGRESS;
        if (columnId.equals(b.getColumnReviewId())) return TaskStatus.REVIEW;
        if (columnId.equals(b.getColumnDoneId())) return TaskStatus.DONE;
        return TaskStatus.TODO;
    }

    private String statusToColumn(TaskStatus status, UserBoardSettings b) {
        return switch (status != null ? status : TaskStatus.TODO) {
            case TODO -> b.getColumnTodoId();
            case IN_PROGRESS -> b.getColumnInProgressId();
            case REVIEW -> b.getColumnReviewId();
            case DONE -> b.getColumnDoneId();
        };
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
