package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileTaskDto(
        String id,
        String title,
        String columnId,
        Deadline deadline,
        List<String> assigned,
        String description,
        Boolean completed,
        Boolean archived,
        Boolean deleted
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Deadline(Long deadline, Long startDate, Boolean withTime) {}

    public Long deadlineMillis() {
        return deadline != null ? deadline.deadline() : null;
    }

    public Long startDateMillis() {
        return deadline != null ? deadline.startDate() : null;
    }
}
