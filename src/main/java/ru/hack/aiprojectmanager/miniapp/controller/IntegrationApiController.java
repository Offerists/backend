package ru.hack.aiprojectmanager.miniapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.hack.aiprojectmanager.miniapp.dto.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/yougile")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IntegrationApiController {

    private final MiniAppService service;

    @GetMapping
    public YouGileStatusResponse getStatus(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.getYougileStatus(telegramUserId);
    }

    @PostMapping("/connect")
    public ConnectResponse connect(
            @RequestBody ConnectRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.connectYougile(telegramUserId, body);
    }

    @GetMapping("/boards")
    public Map<String, List<BoardDto>> getBoards(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return Map.of("boards", service.getBoards(telegramUserId));
    }

    @PutMapping("/boards/{boardId}/select")
    public ResponseEntity<Void> selectBoard(
            @PathVariable String boardId,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        service.selectBoard(telegramUserId, boardId);
        return ResponseEntity.ok().build();
    }
}
