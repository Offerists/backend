package ru.hack.aiprojectmanager.miniapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.hack.aiprojectmanager.miniapp.dto.CreateTaskRequest;
import ru.hack.aiprojectmanager.miniapp.dto.TaskDto;
import ru.hack.aiprojectmanager.miniapp.dto.UpdateTaskStatusRequest;

import java.util.List;
import java.util.Map;

@Tag(name = "Tasks", description = "Задачи текущей доски пользователя")
@RestController
@RequestMapping("/api/v1/tasks")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TaskApiController {

    private final MiniAppService service;

    @Operation(
            summary = "Получить список задач",
            description = "Возвращает задачи с текущей доски пользователя. "
                    + "Фильтр `active` — задачи не в статусе DONE; `all` — все задачи.",
            parameters = @Parameter(
                    name = "filter",
                    description = "Фильтр: `active` (по умолчанию) или `all`",
                    schema = @Schema(type = "string", allowableValues = {"active", "all"}, defaultValue = "active")
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список задач",
                            content = @Content(schema = @Schema(example = """
                                    {
                                      "tasks": [
                                        {
                                          "id": "abc123",
                                          "title": "Доработать UI",
                                          "description": "Правки по макету",
                                          "status": "IN_PROGRESS",
                                          "statusLabel": "В работе",
                                          "deadline": "2025-07-01T00:00:00",
                                          "assigneeIds": ["user-uuid-1"]
                                        }
                                      ]
                                    }
                                    """))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или невалидный X-Telegram-Init-Data",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @GetMapping
    public Map<String, List<TaskDto>> getTasks(
            @RequestParam(defaultValue = "active") String filter,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return Map.of("tasks", service.getTasks(telegramUserId, filter));
    }

    @Operation(
            summary = "Создать задачу",
            description = "Создаёт новую задачу в YouGile в текущей выбранной доске пользователя. "
                    + "Если `columnId` не указан, задача создаётся в колонке TODO.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Созданная задача",
                            content = @Content(schema = @Schema(implementation = TaskDto.class))),
                    @ApiResponse(responseCode = "400", description = "Невалидные данные",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "title is required"}
                                    """))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public TaskDto createTask(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Данные новой задачи",
                    content = @Content(
                            schema = @Schema(implementation = CreateTaskRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "Доработать UI кнопок",
                                      "description": "Правки по макету v2",
                                      "deadlineMs": 1751328000000,
                                      "assigneeIds": ["user-uuid-1"]
                                    }
                                    """)
                    )
            )
            @RequestBody CreateTaskRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.createTask(telegramUserId, body);
    }

    @Operation(
            summary = "Обновить статус задачи",
            description = "Меняет статус задачи по её ID. "
                    + "Допустимые значения: `TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Обновлённая задача",
                            content = @Content(schema = @Schema(implementation = TaskDto.class))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Invalid or expired init data"}
                                    """))),
                    @ApiResponse(responseCode = "404", description = "Задача не найдена",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Task not found"}
                                    """)))
            }
    )
    @PatchMapping("/{taskId}/status")
    public TaskDto updateStatus(
            @Parameter(description = "ID задачи в YouGile", required = true)
            @PathVariable String taskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Новый статус",
                    content = @Content(
                            schema = @Schema(implementation = UpdateTaskStatusRequest.class),
                            examples = @ExampleObject(value = """
                                    {"status": "IN_PROGRESS"}
                                    """)
                    )
            )
            @RequestBody UpdateTaskStatusRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.updateTaskStatus(telegramUserId, taskId, body.getStatus());
    }
}
