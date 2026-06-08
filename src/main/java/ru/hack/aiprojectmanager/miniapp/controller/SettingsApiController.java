package ru.hack.aiprojectmanager.miniapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.hack.aiprojectmanager.miniapp.dto.NotificationSettingsRequest;
import ru.hack.aiprojectmanager.miniapp.dto.NotificationSettingsResponse;

@Tag(name = "Settings", description = "Настройки пользователя")
@RestController
@RequestMapping("/api/v1/settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SettingsApiController {

    private final MiniAppService service;

    @Operation(
            summary = "Получить настройки уведомлений",
            description = "Возвращает текущие настройки Telegram-уведомлений пользователя.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Настройки уведомлений",
                            content = @Content(schema = @Schema(implementation = NotificationSettingsResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @GetMapping("/notifications")
    public NotificationSettingsResponse getNotifications(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.getNotificationSettings(telegramUserId);
    }

    @Operation(
            summary = "Обновить настройки уведомлений",
            description = "Изменяет настройки уведомлений. Передавайте только те поля, которые нужно изменить.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Обновлённые настройки",
                            content = @Content(schema = @Schema(implementation = NotificationSettingsResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @PatchMapping("/notifications")
    public NotificationSettingsResponse updateNotifications(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Поля для обновления (null-поля игнорируются)",
                    content = @Content(schema = @Schema(implementation = NotificationSettingsRequest.class))
            )
            @RequestBody NotificationSettingsRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.updateNotificationSettings(telegramUserId, body);
    }
}
