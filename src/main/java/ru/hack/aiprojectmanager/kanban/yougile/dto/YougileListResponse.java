package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YougileListResponse<T>(List<T> content) {}
