package ru.hack.aiprojectmanager.miniapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import ru.hack.aiprojectmanager.miniapp.dto.UserDto;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;

import java.util.List;
import java.util.Map;

@Tag(name = "Users", description = "Пользователи YouGile-компании")
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UsersApiController {

    private final MiniAppService service;

    @Operation(
            summary = "Список пользователей компании",
            description = "Возвращает всех пользователей YouGile-компании текущего пользователя. "
                    + "Используется для выбора исполнителя при создании задачи.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список пользователей",
                            content = @Content(schema = @Schema(example = """
                                    {
                                      "users": [
                                        {"id": "user-uuid-1", "name": "Иван Петров", "email": "ivan@example.com"},
                                        {"id": "user-uuid-2", "name": "Мария Сидорова", "email": "maria@example.com"}
                                      ]
                                    }
                                    """))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """))),
                    @ApiResponse(responseCode = "409", description = "YouGile не подключён",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "YouGile not connected. Complete onboarding first."}
                                    """)))
            }
    )
    @GetMapping
    public Map<String, List<UserDto>> getUsers(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return Map.of("users", service.getUsers(telegramUserId));
    }
}
