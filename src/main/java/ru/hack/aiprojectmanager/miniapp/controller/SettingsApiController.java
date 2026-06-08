package ru.hack.aiprojectmanager.miniapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.hack.aiprojectmanager.miniapp.dto.NotificationSettingsRequest;
import ru.hack.aiprojectmanager.miniapp.dto.NotificationSettingsResponse;

@RestController
@RequestMapping("/api/v1/settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SettingsApiController {

    private final MiniAppService service;

    @GetMapping("/notifications")
    public NotificationSettingsResponse getNotifications(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.getNotificationSettings(telegramUserId);
    }

    @PatchMapping("/notifications")
    public NotificationSettingsResponse updateNotifications(
            @RequestBody NotificationSettingsRequest body,
            HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.updateNotificationSettings(telegramUserId, body);
    }
}
