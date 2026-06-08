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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hack.aiprojectmanager.miniapp.dto.ProfileResponse;

@Tag(name = "User", description = "Профиль пользователя")
@RestController
@RequestMapping("/api/v1/user")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserApiController {

    private final MiniAppService service;

    @Operation(
            summary = "Профиль текущего пользователя",
            description = "Возвращает профиль аутентифицированного пользователя: Telegram-данные, "
                    + "статус интеграции с YouGile и статистику задач.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Профиль пользователя",
                            content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @GetMapping("/profile")
    public ProfileResponse getProfile(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.getProfile(telegramUserId);
    }
}
