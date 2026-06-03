package ru.hack.aiprojectmanager.kanban;

import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;

import java.util.List;

public interface KanbanProvider {

    String createTask(Long chatId, Task task);

    void moveTask(Long chatId, String externalTaskId, TaskStatus newStatus);

    List<Task> getTasksByAssignee(Long chatId, String externalUserId);

    Task getTask(Long chatId, String externalTaskId);

    void updateTask(Long chatId, String externalTaskId, Task updated);
}
