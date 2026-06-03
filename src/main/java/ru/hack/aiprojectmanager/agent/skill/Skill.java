package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;

public interface Skill {

    String getName();

    String getDescription();

    JsonNode getParametersSchema();

    String execute(Long telegramUserId, JsonNode arguments);

    default String requireText(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalArgumentException("не указано обязательное поле «" + field + "»");
        }
        return node.asText().trim();
    }

    default String optText(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() && !node.asText().isBlank() ? node.asText().trim() : null;
    }
}
