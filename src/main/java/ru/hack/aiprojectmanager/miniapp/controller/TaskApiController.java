package ru.hack.aiprojectmanager.miniapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.hack.aiprojectmanager.miniapp.dto.TaskDto;
import ru.hack.aiprojectmanager.miniapp.dto.UpdateTaskStatusRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TaskApiController {

    private final MiniAppService service;

    @GetMapping
    public Map<String, List<TaskDto>> getTasks(
            @RequestParam(defaultValue = "active") String filter,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return Map.of("tasks", service.getTasks(telegramUserId, filter));
    }

    @PatchMapping("/{taskId}/status")
    public TaskDto updateStatus(
            @PathVariable String taskId,
            @RequestBody UpdateTaskStatusRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.updateTaskStatus(telegramUserId, taskId, body.getStatus());
    }
}
