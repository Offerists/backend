package ru.hack.aiprojectmanager.kanban.yougile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileBoardDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileListResponse;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskRequest;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;

import java.util.List;

@Component
public class YougileClient {

    private final RestClient restClient;

    public YougileClient(RestClient.Builder builder,
                         @Value("${yougile.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
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
        return listOf(restClient.get()
                .uri(b -> b.path("/tasks").queryParam("columnId", columnId).build())
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<YougileListResponse<YougileTaskDto>>() {}));
    }

    public List<YougileBoardDto> getBoards(String apiKey) {
        return listOf(restClient.get()
                .uri("/boards")
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<YougileListResponse<YougileBoardDto>>() {}));
    }

    public List<YougileColumnDto> getColumns(String apiKey, String boardId) {
        return listOf(restClient.get()
                .uri(b -> b.path("/columns").queryParam("boardId", boardId).build())
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<YougileListResponse<YougileColumnDto>>() {}));
    }

    public List<YougileUserDto> getUsers(String apiKey) {
        return listOf(restClient.get()
                .uri("/users")
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<YougileListResponse<YougileUserDto>>() {}));
    }

    private static <T> List<T> listOf(YougileListResponse<T> response) {
        return response != null && response.content() != null ? response.content() : List.of();
    }
}
