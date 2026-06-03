package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileTaskDto(
        String id,
        String title,
        String columnId,
        Long deadline,
        Long startDate,
        Map<String, Boolean> assigned,
        String description,
        Boolean deleted
) {}
