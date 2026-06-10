package ru.hack.aiprojectmanager.miniapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hack.aiprojectmanager.miniapp.dto.*;

import java.util.List;
import java.util.Map;

@Tag(name = "YouGile Integration", description = "Подключение и управление интеграцией с YouGile")
@RestController
@RequestMapping("/api/v1/integrations/yougile")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IntegrationApiController {

    private final MiniAppService service;

    @Operation(
            summary = "Статус интеграции с YouGile",
            description = "Возвращает текущее состояние подключения пользователя к YouGile.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Статус интеграции",
                            content = @Content(schema = @Schema(implementation = YouGileStatusResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "connected": true,
                                              "companyId": "company-uuid",
                                              "yougileUserId": "user-uuid",
                                              "role": "admin"
                                            }
                                            """))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @GetMapping
    public YouGileStatusResponse getStatus(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.getYougileStatus(telegramUserId);
    }

    @Operation(
            summary = "Подключить YouGile аккаунт",
            description = """
                    Подключает YouGile аккаунт пользователя.

                    **Шаг 1**: передать `email` + `password` без `companyId`.
                    Если у пользователя несколько компаний, вернётся `requiresCompanySelection: true`
                    и список компаний.

                    **Шаг 2**: повторить запрос с тем же `email`/`password` и выбранным `companyId`.
                    При успехе `connected: true` и список доступных досок.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Результат подключения",
                            content = @Content(schema = @Schema(implementation = ConnectResponse.class),
                                    examples = {
                                            @ExampleObject(name = "Одна компания — успех", value = """
                                                    {
                                                      "connected": true,
                                                      "requiresCompanySelection": false,
                                                      "companies": [],
                                                      "boards": [
                                                        {"id": "board-uuid", "name": "Бэклог", "isDefault": true}
                                                      ]
                                                    }
                                                    """),
                                            @ExampleObject(name = "Несколько компаний", value = """
                                                    {
                                                      "connected": false,
                                                      "requiresCompanySelection": true,
                                                      "companies": [
                                                        {"id": "c1", "name": "Компания А"},
                                                        {"id": "c2", "name": "Компания Б"}
                                                      ],
                                                      "boards": []
                                                    }
                                                    """)
                                    })),
                    @ApiResponse(responseCode = "400", description = "Неверные учётные данные",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Invalid credentials"}
                                    """))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @PostMapping("/connect")
    public ConnectResponse connect(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Учётные данные YouGile",
                    content = @Content(schema = @Schema(implementation = ConnectRequest.class),
                            examples = {
                                    @ExampleObject(name = "Шаг 1 — без companyId", value = """
                                            {"email": "user@example.com", "password": "secret"}
                                            """),
                                    @ExampleObject(name = "Шаг 2 — с companyId", value = """
                                            {"email": "user@example.com", "password": "secret", "companyId": "c1"}
                                            """)
                            })
            )
            @RequestBody ConnectRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.connectYougile(telegramUserId, body);
    }

    @Operation(
            summary = "Список доступных досок",
            description = "Возвращает все доски текущей компании пользователя. "
                    + "Поле `isDefault` указывает выбранную доску.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список досок",
                            content = @Content(schema = @Schema(example = """
                                    {
                                      "boards": [
                                        {"id": "board-1", "name": "Sprint 1", "isDefault": true},
                                        {"id": "board-2", "name": "Бэклог", "isDefault": false}
                                      ]
                                    }
                                    """))),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @GetMapping("/boards")
    public Map<String, List<BoardDto>> getBoards(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return Map.of("boards", service.getBoards(telegramUserId));
    }

    @Operation(
            summary = "Отключить YouGile аккаунт",
            description = "Удаляет интеграцию с YouGile: очищает API-ключ, роль и выбранные доски пользователя.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Интеграция удалена"),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """)))
            }
    )
    @DeleteMapping
    public ResponseEntity<Void> disconnect(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        service.disconnectYougile(telegramUserId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Выбрать доску",
            description = "Устанавливает указанную доску как текущую для пользователя. "
                    + "Задачи в `/api/v1/tasks` будут браться с этой доски.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Доска выбрана"),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Missing X-Telegram-Init-Data header"}
                                    """))),
                    @ApiResponse(responseCode = "404", description = "Доска не найдена",
                            content = @Content(schema = @Schema(example = """
                                    {"error": "Board not found"}
                                    """)))
            }
    )
    @PutMapping("/boards/{boardId}/select")
    public ResponseEntity<Void> selectBoard(
            @Parameter(description = "ID доски в YouGile", required = true)
            @PathVariable String boardId,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        service.selectBoard(telegramUserId, boardId);
        return ResponseEntity.ok().build();
    }
}
