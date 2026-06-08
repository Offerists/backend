package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class GetUserTasksSkill {

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileMapper mapper;

    @Tool(name = "get_user_tasks", description = "Получить задачи. "
            + "name='me' — мои, name=имя/@username — участника, без name — все. "
            + "filter: active (по умолчанию), done — завершённые, all — все. "
            + "ВСЕГДА вызывай заново, не используй кэш из истории.")
    public String getUserTasks(
            @Nullable @ToolParam(description = "'me', имя или @username; без параметра — все участники", required = false) String name,
            @Nullable @ToolParam(description = "active | done | all", required = false) String filter,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");

        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null || requester.getYougileApiKey() == null) return "Активных задач нет";

        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(telegramUserId).orElse(null);
        if (board == null) return "Активных задач нет";

        String apiKey = requester.getYougileApiKey();
        String targetYougileId = resolveTarget(requester, name, telegramUserId);
        String mode = filter != null ? filter.toLowerCase(Locale.ROOT) : "active";

        List<Task> tasks;
        if (targetYougileId != null) {
            // Один запрос с assignedTo — намного эффективнее чем N запросов по колонкам
            tasks = yougileClient.getTasksByAssignee(apiKey, targetYougileId)
                    .stream()
                    .map(dto -> mapper.toDomain(dto, board))
                    .filter(t -> switch (mode) {
                        case "done" -> t.getStatus() == TaskStatus.DONE;
                        case "all"  -> true;
                        default     -> t.getStatus() != TaskStatus.DONE;
                    })
                    .toList();
        } else {
            // Все задачи — нужно перебрать по колонкам
            List<YougileColumnDto> columns = yougileClient.getColumns(apiKey, board.getBoardId());
            if (columns.isEmpty()) {
                columns = Stream.of(board.getColumnTodoId(), board.getColumnInProgressId(),
                                board.getColumnReviewId(), board.getColumnDoneId())
                        .filter(Objects::nonNull).map(id -> new YougileColumnDto(id, id)).toList();
            }
            tasks = yougileClient
                    .getTasksByColumns(apiKey, columns.stream().map(YougileColumnDto::id).toList())
                    .stream()
                    .map(dto -> mapper.toDomain(dto, board))
                    .filter(t -> switch (mode) {
                        case "done" -> t.getStatus() == TaskStatus.DONE;
                        case "all"  -> true;
                        default     -> t.getStatus() != TaskStatus.DONE;
                    })
                    .toList();
        }

        if (tasks.isEmpty()) return "Активных задач нет";

        var sb = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            sb.append(i + 1).append(". «").append(t.getTitle()).append("» — ")
                    .append(statusLabel(t.getStatus().name()));
            if (t.getDeadline() != null) {
                sb.append(", до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            }
            sb.append(" {tid:").append(t.getExternalId()).append("}\n");
        }
        return sb.toString().trim();
    }

    private String resolveTarget(AppUser requester, String name, Long telegramUserId) {
        if (name == null) return null;
        if (name.equalsIgnoreCase("me") || name.equalsIgnoreCase("я")) {
            return requester.getYougileUserId();
        }
        String q = name.startsWith("@") ? name.substring(1).toLowerCase() : name.toLowerCase();
        List<AppUser> candidates = requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                : List.of();
        return candidates.stream()
                .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                        || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)))
                .map(AppUser::getYougileUserId).filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private boolean isAssignedTo(Task t, String id) {
        return (t.getAssigneeId() != null && t.getAssigneeId().equals(id))
                || (t.getAssigneeIds() != null && t.getAssigneeIds().contains(id));
    }

    private String statusLabel(String s) {
        return switch (s) {
            case "TODO" -> "К выполнению";
            case "IN_PROGRESS" -> "В работе";
            case "REVIEW" -> "На проверке";
            case "DONE" -> "Готово";
            default -> s;
        };
    }
}
