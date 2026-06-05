package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;
import ru.hack.aiprojectmanager.notification.NotificationSender;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignTaskSkill {

    private final KanbanProvider kanban;
    private final AppUserRepository appUserRepository;
    private final NotificationSender notificationSender;
    private final YougileClient yougileClient;

    @Tool(name = "assign_task", description = "Назначить исполнителя на существующую задачу. task_id из find_task.")
    public String assignTask(
            @ToolParam(description = "task_id из find_task") String taskId,
            @ToolParam(description = "Имя, @username или telegram_id исполнителя") String assignee,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");

        // Ищем сначала в AppUser, потом в YouGile
        AppUser target = findAppUser(telegramUserId, assignee);
        String yougileId = target != null ? target.getYougileUserId()
                : resolveFromYougile(telegramUserId, assignee);

        if (yougileId == null) {
            return "⚠️ Пользователь «" + assignee + "» не найден ни в системе, ни в YouGile.";
        }

        Task current = kanban.getTask(telegramUserId, taskId);
        if (current == null) return "Задача не найдена.";

        Task updated = Task.builder()
                .title(current.getTitle()).description(current.getDescription())
                .status(current.getStatus()).columnId(current.getColumnId())
                .deadline(current.getDeadline())
                .assigneeId(yougileId).assigneeIds(List.of(yougileId))
                .build();

        kanban.updateTask(telegramUserId, taskId, updated);
        notifyAssignee(target, current.getTitle(), telegramUserId);

        String displayName = target != null && target.getFullName() != null
                ? target.getFullName() : assignee;
        return "Задача «" + current.getTitle() + "» назначена на " + displayName + ".";
    }

    private String resolveFromYougile(Long telegramUserId, String query) {
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null || requester.getYougileApiKey() == null) return null;
        String q = query.startsWith("@") ? query.substring(1) : query;
        return yougileClient.findUsersByName(requester.getYougileApiKey(), q)
                .stream().map(YougileUserDto::id).findFirst().orElse(null);
    }

    private AppUser findAppUser(Long telegramUserId, String query) {
        try {
            long id = Long.parseLong(query);
            return appUserRepository.findFirstByTelegramId(id).orElse(null);
        } catch (NumberFormatException ignored) {}
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null) return null;
        String q = query.startsWith("@") ? query.substring(1).toLowerCase() : query.toLowerCase();
        List<AppUser> candidates = requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId()) : List.of();
        return candidates.stream()
                .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(q))
                        || (u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(q)))
                .findFirst().orElse(null);
    }

    private void notifyAssignee(AppUser target, String taskTitle, Long assignerTelegramId) {
        if (target == null || target.getChatId() == null) return;
        if (target.getTelegramId().equals(assignerTelegramId)) return;
        AppUser assigner = appUserRepository.findFirstByTelegramId(assignerTelegramId).orElse(null);
        String assignerName = assigner != null && assigner.getFullName() != null
                ? assigner.getFullName() : "Лид";
        try {
            notificationSender.send(target.getChatId(),
                    "📌 " + assignerName + " назначил(а) тебя на задачу «" + taskTitle + "».");
        } catch (Exception e) {
            log.warn("Failed to notify assignee {}: {}", target.getTelegramId(), e.getMessage());
        }
    }
}
