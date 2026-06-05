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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class YougileClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long COLUMNS_TTL_MS = 5 * 60 * 1000L; // 5 минут

    private final RestClient restClient;
    private final Map<String, CachedColumns> columnsCache = new ConcurrentHashMap<>();

    private record CachedColumns(List<YougileColumnDto> columns, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > COLUMNS_TTL_MS;
        }
    }

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
        // /task-list — актуальный эндпоинт (GET /tasks deprecated)
        return fetchList("/task-list", apiKey, YougileTaskDto.class, "columnId", columnId);
    }

    public List<YougileTaskDto> getTasksByAssignee(String apiKey, String yougileUserId) {
        // Прямой запрос задач конкретного пользователя без клиентской фильтрации
        return fetchList("/task-list", apiKey, YougileTaskDto.class, "assignedTo", yougileUserId);
    }

    /**
     * Загружает задачи из нескольких колонок параллельно (на виртуальных потоках).
     * Порядок результата соответствует порядку колонок.
     */
    public List<YougileTaskDto> getTasksByColumns(String apiKey, List<String> columnIds) {
        List<String> ids = columnIds.stream().filter(c -> c != null && !c.isBlank()).toList();
        if (ids.isEmpty()) return List.of();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<YougileTaskDto>>> futures = ids.stream()
                    .map(columnId -> executor.submit(() -> getTasksByColumn(apiKey, columnId)))
                    .toList();

            List<YougileTaskDto> result = new ArrayList<>();
            for (Future<List<YougileTaskDto>> f : futures) {
                try {
                    result.addAll(f.get());
                } catch (Exception e) {
                    log.warn("Parallel task fetch failed: {}", e.getMessage());
                }
            }
            return result;
        }
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
        CachedColumns cached = columnsCache.get(boardId);
        if (cached != null && !cached.isExpired()) {
            log.debug("Columns cache hit for board {}", boardId);
            return cached.columns();
        }
        List<YougileColumnDto> columns = fetchList("/columns", apiKey, YougileColumnDto.class, "boardId", boardId);
        if (!columns.isEmpty()) {
            columnsCache.put(boardId, new CachedColumns(columns, System.currentTimeMillis()));
            log.info("Columns cached for board {} ({} columns)", boardId, columns.size());
        }
        return columns;
    }

    public List<YougileUserDto> getUsers(String apiKey) {
        return fetchList("/users", apiKey, YougileUserDto.class);
    }

    /** Найти YouGile пользователей по части realName (поиск без учёта регистра на клиенте) */
    public List<YougileUserDto> findUsersByName(String apiKey, String query) {
        return getUsers(apiKey).stream()
                .filter(u -> u.realName() != null
                        && u.realName().toLowerCase(java.util.Locale.ROOT)
                                .contains(query.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    public YougileUserDto getCurrentUser(String apiKey) {
        // GET /users/me — точный способ получить текущего пользователя по токену
        String uri = "/users/me";
        log.info("YouGile GET {}", uri);
        try {
            return restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(YougileUserDto.class);
        } catch (Exception e) {
            log.error("Failed to fetch current user: {}", e.getMessage());
            return null;
        }
    }

    // Универсальный метод: поддерживает ответы вида {"content":[...]} и просто [...]
    private <T> List<T> fetchList(String path, String apiKey, Class<T> type, String... queryParams) {
        // Строим query string вручную и используем строковую форму .uri() (как рабочие методы),
        // чтобы избежать проблем с UriBuilder при baseUrl с path-компонентом /api-v2
        StringBuilder uri = new StringBuilder(path);
        for (int i = 0; i + 1 < queryParams.length; i += 2) {
            uri.append(i == 0 ? '?' : '&')
               .append(queryParams[i]).append('=')
               .append(URLEncoder.encode(queryParams[i + 1], StandardCharsets.UTF_8));
        }
        String fullUri = uri.toString();
        log.info("YouGile GET {}", fullUri);
        try {
            JsonNode body = restClient.get()
                    .uri(fullUri)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            return parseList(body, type, path);
        } catch (Exception e) {
            log.error("Failed to fetch {}: {}", fullUri, e.getMessage());
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
