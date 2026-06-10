package ru.hack.aiprojectmanager.miniapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Обновление настроек уведомлений (null-поля игнорируются)")
public class NotificationSettingsRequest {
    @Schema(description = "Включить/выключить дайджест задач", example = "true")
    private Boolean digestEnabled;

    @Schema(description = "Включить/выключить напоминания о дедлайнах", example = "false")
    private Boolean remindersEnabled;

    @Schema(description = "Часовой пояс в формате IANA", example = "Europe/Moscow")
    private String timezone;
}
