package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YougileTaskRequest {
    private String title;
    private String columnId;
    private Long deadline;
    private Long startDate;
    // YouGile API v2 ожидает массив id пользователей: "assigned": ["<uuid>", ...]
    private List<String> assigned;
    private String description;
}
