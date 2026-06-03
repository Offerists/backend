package ru.hack.aiprojectmanager.agent.groq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.hack.aiprojectmanager.agent.groq.dto.ChatRequest;
import ru.hack.aiprojectmanager.agent.groq.dto.ChatResponse;

@Component
public class GroqLLMClient {

    private final RestClient restClient;
    private final String model;

    public GroqLLMClient(
            RestClient.Builder builder,
            @Value("${groq.base-url}") String baseUrl,
            @Value("${groq.api-key}") String apiKey,
            @Value("${groq.model}") String model) {
        this.model = model;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public ChatResponse chat(ChatRequest request) {
        return restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
    }

    public String getModel() {
        return model;
    }
}
