package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileUserDto(
        String id,
        String email,
        String realName,   // ФИО пользователя (поле realName по API)
        Boolean isAdmin    // true = администратор компании
) {}
