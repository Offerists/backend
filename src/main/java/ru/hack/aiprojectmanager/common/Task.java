package ru.hack.aiprojectmanager.common;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Task {
    private String id;
    private String title;
    private String description;
    private TaskStatus status;

    private String assigneeId;
    private List<String> assigneeIds;

    private LocalDateTime deadline;
    private LocalDateTime startDate;

    private String columnId;
    private String externalId;
}
