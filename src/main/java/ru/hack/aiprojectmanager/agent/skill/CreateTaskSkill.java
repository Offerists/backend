package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.agent.AgentContextService;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;
import ru.hack.aiprojectmanager.user.UserMatcher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateTaskSkill {

    private final KanbanProvider kanban;
    private final AppUserRepository appUserRepository;
    private final YougileClient yougileClient;
    private final AgentContextService agentContextService;

    @Tool(name = "create_task", description = "Создать новую задачу на канбан-доске")
    public String createTask(
            @ToolParam(description = "Название задачи") String title,
            @Nullable @ToolParam(description = "Описание задачи", required = false) String description,
            @Nullable @ToolParam(description = "Исполнитель: имя, @username или telegram_id", required = false) String assignee,
            @Nullable @ToolParam(description = "Дедлайн в формате yyyy-MM-ddTHH:mm", required = false) String deadline,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");
        Boolean isLeadCtx = (Boolean) ctx.getContext().get("isLead");
        boolean isLead = isLeadCtx == null || isLeadCtx;

        boolean assigneeRequested = assignee != null && !assignee.isBlank();
        String yougileAssigneeId;
        if (!isLead) {
            // Участники создают задачи только себе
            AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
            yougileAssigneeId = requester != null ? requester.getYougileUserId() : null;
        } else {
            yougileAssigneeId = resolveAssignee(telegramUserId, assignee);
        }

        boolean assigneeResolved = yougileAssigneeId != null;

        Task task = Task.builder()
                .title(title)
                .description(description)
                .assigneeId(yougileAssigneeId)
                .status(TaskStatus.TODO)
                .deadline(parseDeadline(deadline))
                .build();

        String createdId = kanban.createTask(telegramUserId, task);
        agentContextService.rememberTask(telegramUserId, createdId, title);

        if (!isLead && assigneeRequested && !UserMatcher.isSelfReference(assignee)) {
            return "Задача «" + title + "» создана и назначена на тебя. "
                    + "Назначать задачи другим участникам может только лид.";
        }
        if (assigneeRequested && !assigneeResolved) {
            return "⚠️ Задача «" + title + "» создана БЕЗ исполнителя. "
                    + "Участник «" + assignee + "» не найден ни в системе, ни в YouGile. "
                    + "Сообщи пользователю что исполнитель не назначен.";
        }
        return "Задача «" + title + "» создана";
    }

    private String resolveAssignee(Long telegramUserId, String query) {
        if (query == null || query.isBlank()) return null;

        // 1. Числовой telegram_id → ищем в AppUser
        try {
            long id = Long.parseLong(query);
            return appUserRepository.findFirstByTelegramId(id)
                    .map(AppUser::getYougileUserId).orElse(null);
        } catch (NumberFormatException ignored) {}

        // 2. По имени/username/email в AppUser (telegram и YouGile)
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester != null) {
            List<AppUser> candidates = requester.getYougileCompanyId() != null
                    ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                    : List.of();
            String fromDb = candidates.stream()
                    .filter(u -> UserMatcher.matches(u, query))
                    .map(AppUser::getYougileUserId)
                    .filter(id -> id != null)
                    .findFirst().orElse(null);
            if (fromDb != null) return fromDb;

            // 3. Fallback: ищем прямо в YouGile по realName
            if (requester.getYougileApiKey() != null) {
                String q = query.startsWith("@") ? query.substring(1) : query;
                return yougileClient.findUsersByName(requester.getYougileApiKey(), q)
                        .stream().map(YougileUserDto::id).findFirst().orElse(null);
            }
        }
        return null;
    }

    private LocalDateTime parseDeadline(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("Could not parse deadline '{}', ignoring", raw);
            return null;
        }
    }
}
