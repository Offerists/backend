package ru.hack.aiprojectmanager.kanban.yougile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileBoardDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileProjectDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskRequest;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class YougileClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        return fetchList("/tasks", apiKey, YougileTaskDto.class, "columnId", columnId);
    }

    public List<YougileProjectDto> getProjects(String apiKey) {
        return fetchList("/projects", apiKey, YougileProjectDto.class);
    }

    public List<YougileBoardDto> getBoards(String apiKey) {
        return fetchList("/boards", apiKey, YougileBoardDto.class);
    }

    public List<YougileBoardDto> getBoardsByProject(String apiKey, String projectId) {
        return fetchList("/boards", apiKey, YougileBoardDto.class, "projectId", projectId);
    }

    public List<YougileColumnDto> getColumns(String apiKey, String boardId) {
        return fetchList("/columns", apiKey, YougileColumnDto.class, "boardId", boardId);
    }

    public List<YougileUserDto> getUsers(String apiKey) {
        return fetchList("/users", apiKey, YougileUserDto.class);
    }

    // Универсальный метод: поддерживает ответы вида {"content":[...]} и просто [...]
    private <T> List<T> fetchList(String path, String apiKey, Class<T> type, String... queryParams) {
        try {
            JsonNode body = restClient.get()
                    .uri(b -> {
                        var builder = b.path(path);
                        for (int i = 0; i + 1 < queryParams.length; i += 2) {
                            builder = builder.queryParam(queryParams[i], queryParams[i + 1]);
                        }
                        return builder.build();
                    })
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            return parseList(body, type, path);
        } catch (Exception e) {
            log.error("Failed to fetch {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    private <T> List<T> parseList(JsonNode body, Class<T> type, String path) {
        if (body == null) return List.of();
        JsonNode array = body.isArray() ? body : body.path("content");
        log.debug("parseList {} → {} items in response", path, array.isArray() ? array.size() : "non-array");
        if (!array.isArray()) {
            log.warn("Unexpected response structure for {}: {}", path, body);
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (JsonNode item : array) {
            try {
                result.add(MAPPER.treeToValue(item, type));
            } catch (Exception e) {
                log.warn("Failed to parse {} item: {}", type.getSimpleName(), e.getMessage());
            }
        }
        return result;
    }
}
