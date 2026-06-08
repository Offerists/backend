package ru.hack.aiprojectmanager.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextService {

    private final AgentContextRepository repository;

    public void rememberTask(Long telegramId, String taskId, String taskTitle) {
        if (telegramId == null || taskId == null) return;
        AgentContext ctx = load(telegramId);
        ctx.setLastTaskId(taskId);
        if (taskTitle != null) ctx.setLastTaskTitle(taskTitle);
        repository.save(ctx);
        log.debug("AgentContext task: telegramId={} taskId={} title={}", telegramId, taskId, taskTitle);
    }

    public void rememberAssignee(Long telegramId, String yougileId, String name) {
        if (telegramId == null || yougileId == null) return;
        AgentContext ctx = load(telegramId);
        ctx.setLastAssigneeId(yougileId);
        ctx.setLastAssigneeName(name);
        repository.save(ctx);
        log.debug("AgentContext assignee: telegramId={} uid={} name={}", telegramId, yougileId, name);
    }

    public void rememberStatus(Long telegramId, String taskId, String status) {
        if (telegramId == null) return;
        AgentContext ctx = load(telegramId);
        if (taskId != null) ctx.setLastTaskId(taskId);
        if (status != null) ctx.setLastStatus(status);
        repository.save(ctx);
        log.debug("AgentContext status: telegramId={} taskId={} status={}", telegramId, taskId, status);
    }

    @Deprecated(forRemoval = true)
    public void remember(Long telegramId, String taskId, String taskTitle) {
        rememberTask(telegramId, taskId, taskTitle);
    }

    public Optional<AgentContext> get(Long telegramId) {
        return repository.findById(telegramId);
    }

    private AgentContext load(Long telegramId) {
        return repository.findById(telegramId)
                .orElse(AgentContext.builder().telegramId(telegramId).build());
    }
}
