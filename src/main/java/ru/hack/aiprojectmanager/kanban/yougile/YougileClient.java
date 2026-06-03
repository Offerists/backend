package ru.hack.aiprojectmanager.kanban.yougile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileListResponse;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskRequest;

import java.util.List;

@Component
public class YougileClient {
    @Value("${yougile.base-url}")
    private String baseUrl;

    private final RestClient restClient;

    public YougileClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public YougileTaskDto createTask(String apiKey, YougileTaskRequest request) {
        return restClient.post()
                .uri("/tasks")
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(YougileTaskDto.class);
    }

    public YougileTaskDto getTask(String apiKey, String taskId) {
        return restClient.get()
                .uri("/tasks/{id}", taskId)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(YougileTaskDto.class);
    }

    public void updateTask(String apiKey, String taskId, YougileTaskRequest request) {
        restClient.put()
                .uri("/tasks/{id}", taskId)
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public List<YougileTaskDto> getTasksByColumn(String apiKey, String columnId) {
        YougileListResponse<YougileTaskDto> response = restClient.get()
                .uri(b -> b.path("/tasks").queryParam("columnId", columnId).build())
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<YougileListResponse<YougileTaskDto>>() {});
        return response != null && response.content() != null ? response.content() : List.of();
    }
}
