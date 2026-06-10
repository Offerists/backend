package ru.hack.aiprojectmanager.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Пользователь компании в YouGile")
public record UserDto(
        @Schema(description = "YouGile user ID", example = "user-uuid-1") String id,
        @Schema(description = "Имя пользователя", example = "Иван Петров") String name,
        @Schema(description = "Email", example = "ivan@example.com") String email
) {}
