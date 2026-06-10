package ru.hack.aiprojectmanager.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Настройки уведомлений и часовой пояс")
public record NotificationSettingsResponse(
        @Schema(description = "Дайджест задач включён", example = "true") boolean digestEnabled,
        @Schema(description = "Напоминания о дедлайнах включены", example = "true") boolean remindersEnabled,
        @Schema(description = "Часовой пояс в формате IANA", example = "Europe/Moscow") String timezone
) {}
