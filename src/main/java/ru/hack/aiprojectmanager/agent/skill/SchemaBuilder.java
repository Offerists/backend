package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

class SchemaBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final ObjectNode properties = mapper.createObjectNode();
    private final ObjectNode schema = mapper.createObjectNode();

    static SchemaBuilder object() {
        return new SchemaBuilder();
    }

    SchemaBuilder required(String name, String type, String description) {
        return property(name, type, description, true);
    }

    SchemaBuilder optional(String name, String type, String description) {
        return property(name, type, description, false);
    }

    private SchemaBuilder property(String name, String type, String description, boolean required) {
        ObjectNode prop = mapper.createObjectNode().put("description", description);
        if (required) {
            prop.put("type", type);
        } else {
            // Optional: allow null so Groq strict validation doesn't reject absent params
            var typeArr = prop.putArray("type");
            typeArr.add(type);
            typeArr.add("null");
        }
        properties.set(name, prop);

        if (required) {
            var existing = (tools.jackson.databind.node.ArrayNode)
                    schema.withArrayProperty("required");
            existing.add(name);
        }
        return this;
    }

    SchemaBuilder enumValues(String name, List<String> values) {
        ObjectNode prop = (ObjectNode) properties.get(name);
        if (prop != null) {
            var arr = prop.putArray("enum");
            values.forEach(arr::add);
        }
        return this;
    }

    JsonNode build() {
        schema.put("type", "object");
        schema.set("properties", properties);
        return schema;
    }
}
