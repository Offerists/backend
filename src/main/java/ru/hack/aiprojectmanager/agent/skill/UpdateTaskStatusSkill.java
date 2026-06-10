package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.agent.AgentContextService;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

@Component
@RequiredArgsConstructor
public class UpdateTaskStatusSkill {

    private final KanbanProvider kanban;
    private final AppUserRepository appUserRepository;
    private final AgentContextService agentContextService;

    @Tool(name = "update_task_status", description = "Переместить задачу в другой статус. task_id из find_task или get_user_tasks.")
    public String updateTaskStatus(
            @ToolParam(description = "task_id из find_task") String taskId,
            @ToolParam(description = "TODO | IN_PROGRESS | REVIEW | DONE") String status,
            org.springframework.ai.chat.model.ToolContext ctx) {
        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");
        Boolean isLeadCtx = (Boolean) ctx.getContext().get("isLead");
        boolean isLead = isLeadCtx == null || isLeadCtx;

        if (!isLead) {
            Task current = kanban.getTask(telegramUserId, taskId);
            if (current == null) return "Задача не найдена.";

            AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
            String myId = requester != null ? requester.getYougileUserId() : null;
            boolean isMine = myId != null
                    && (myId.equals(current.getAssigneeId())
                        || (current.getAssigneeIds() != null && current.getAssigneeIds().contains(myId)));
            if (!isMine) {
                return "⚠️ Изменять статус можно только своих задач.";
            }
        }

        kanban.moveTask(telegramUserId, taskId, TaskStatus.valueOf(status));
        agentContextService.rememberStatus(telegramUserId, taskId, status);
        return "Статус обновлён";
    }
}
