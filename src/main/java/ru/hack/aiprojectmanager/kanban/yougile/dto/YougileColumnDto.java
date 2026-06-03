package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileColumnDto(String id, String title) {}
