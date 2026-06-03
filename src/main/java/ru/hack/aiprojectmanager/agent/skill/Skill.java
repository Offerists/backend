package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;

public interface Skill {

    String getName();

    String getDescription();

    JsonNode getParametersSchema();

    String execute(Long chatId, JsonNode arguments);

    /**
     * Возвращает обязательное строковое поле. Бросает {@link IllegalArgumentException},
     * если LLM не передала поле (схема required — не гарантия), чтобы вызов превратился
     * в понятную ошибку, а не в NPE.
     */
    default String requireText(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalArgumentException("не указано обязательное поле «" + field + "»");
        }
        return node.asText().trim();
    }

    /** Возвращает необязательное строковое поле или {@code null}, если оно отсутствует/пустое. */
    default String optText(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() && !node.asText().isBlank() ? node.asText().trim() : null;
    }
}
