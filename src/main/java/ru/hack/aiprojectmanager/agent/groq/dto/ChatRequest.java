package ru.hack.aiprojectmanager.agent.groq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(String model, List<ChatMessage> messages, List<Tool> tools) {}
