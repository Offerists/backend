package ru.hack.aiprojectmanager.kanban;

import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;

import java.util.List;


public interface KanbanProvider {

    String createTask(Long telegramUserId, Task task);

    void moveTask(Long telegramUserId, String externalTaskId, TaskStatus newStatus);

    List<Task> getTasksByAssignee(Long telegramUserId, String yougileAssigneeId);

    Task getTask(Long telegramUserId, String externalTaskId);

    void updateTask(Long telegramUserId, String externalTaskId, Task updated);
}
