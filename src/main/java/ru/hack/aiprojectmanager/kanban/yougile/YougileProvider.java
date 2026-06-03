package ru.hack.aiprojectmanager.kanban.yougile;

import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettings;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettingsRepository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class YougileProvider implements KanbanProvider {

    private final YougileClient client;
    private final YougileMapper mapper;
    private final WorkspaceSettingsRepository settingsRepository;

    public YougileProvider(YougileClient client, YougileMapper mapper,
                           WorkspaceSettingsRepository settingsRepository) {
        this.client = client;
        this.mapper = mapper;
        this.settingsRepository = settingsRepository;
    }

    @Override
    public String createTask(Long chatId, Task task) {
        WorkspaceSettings settings = loadSettings(chatId);
        var created = client.createTask(settings.getYougileApiKey(), mapper.toRequest(task, settings));
        return created.id();
    }

    @Override
    public void moveTask(Long chatId, String externalTaskId, TaskStatus newStatus) {
        WorkspaceSettings settings = loadSettings(chatId);
        client.updateTask(settings.getYougileApiKey(), externalTaskId, mapper.toMoveRequest(newStatus, settings));
    }

    @Override
    public List<Task> getTasksByAssignee(Long chatId, String externalUserId) {
        WorkspaceSettings settings = loadSettings(chatId);
        return Stream.of(
                        settings.getColumnTodoId(),
                        settings.getColumnInProgressId(),
                        settings.getColumnReviewId(),
                        settings.getColumnDoneId())
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .flatMap(columnId -> client.getTasksByColumn(settings.getYougileApiKey(), columnId).stream())
                .filter(dto -> Boolean.TRUE.equals(
                        dto.assigned() != null ? dto.assigned().get(externalUserId) : null))
                .map(dto -> mapper.toDomain(dto, settings))
                .toList();
    }

    @Override
    public Task getTask(Long chatId, String externalTaskId) {
        WorkspaceSettings settings = loadSettings(chatId);
        return mapper.toDomain(client.getTask(settings.getYougileApiKey(), externalTaskId), settings);
    }

    @Override
    public void updateTask(Long chatId, String externalTaskId, Task updated) {
        WorkspaceSettings settings = loadSettings(chatId);
        client.updateTask(settings.getYougileApiKey(), externalTaskId, mapper.toRequest(updated, settings));
    }

    private WorkspaceSettings loadSettings(Long chatId) {
        return settingsRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException(
                        "Yougile settings not configured for chat " + chatId));
    }
}
