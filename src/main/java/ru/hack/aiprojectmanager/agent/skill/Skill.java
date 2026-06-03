package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;

public interface Skill {

    String getName();

    String getDescription();

    JsonNode getParametersSchema();

    String execute(Long chatId, JsonNode arguments);
}
