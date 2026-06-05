package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileBoardDto(String id, String title, String name) {
    public String displayName() {
        if (title != null && !title.isBlank()) return title;
        if (name != null && !name.isBlank()) return name;
        return id;
    }
}
