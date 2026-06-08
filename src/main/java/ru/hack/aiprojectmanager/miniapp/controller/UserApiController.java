package ru.hack.aiprojectmanager.miniapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import ru.hack.aiprojectmanager.miniapp.service.MiniAppService;
import ru.hack.aiprojectmanager.miniapp.TelegramAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hack.aiprojectmanager.miniapp.dto.ProfileResponse;

@RestController
@RequestMapping("/api/v1/user")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserApiController {

    private final MiniAppService service;

    @GetMapping("/profile")
    public ProfileResponse getProfile(HttpServletRequest request) {
        Long telegramUserId = (Long) request.getAttribute(TelegramAuthFilter.USER_ID_ATTR);
        return service.getProfile(telegramUserId);
    }
}
