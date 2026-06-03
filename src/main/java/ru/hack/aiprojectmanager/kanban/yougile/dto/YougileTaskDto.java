package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileTaskDto(
        String id,
        String title,
        String columnId,
        Long deadline,
        Long startDate,
        List<String> assigned,
        String description,
        Boolean deleted
) {}
