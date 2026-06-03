package ru.hack.aiprojectmanager.agent.groq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCall(String id, String type, ToolCallFunction function) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCallFunction(String name, String arguments) {}
}
