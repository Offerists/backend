package ru.hack.aiprojectmanager.agent.groq.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record Tool(String type, ToolFunction function) {

    public record ToolFunction(String name, String description, JsonNode parameters) {}

    public static Tool of(String name, String description, JsonNode parameters) {
        return new Tool("function", new ToolFunction(name, description, parameters));
    }
}
