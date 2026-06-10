package ru.hack.aiprojectmanager.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Запрос на создание задачи")
public class CreateTaskRequest {
    @Schema(description = "Название задачи", required = true, example = "Доработать UI кнопок")
    private String title;

    @Schema(description = "Описание задачи", example = "Правки по макету v2")
    private String description;

    @Schema(description = "Дедлайн в миллисекундах (Unix timestamp)", example = "1751328000000")
    private Long deadlineMs;

    @Schema(description = "YouGile ID исполнителей", example = "[\"user-uuid-1\"]")
    private List<String> assigneeIds;

    @Schema(description = "ID колонки в YouGile (по умолчанию — колонка TODO текущей доски)", example = "column-uuid")
    private String columnId;
}
