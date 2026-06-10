package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.agent.AgentContextService;
import ru.hack.aiprojectmanager.task.Task;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class FindTaskSkill {

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileMapper mapper;
    private final AgentContextService agentContextService;

    @Tool(name = "find_task", description = "Найти задачи по ключевому слову. "
            + "Используй перед созданием (без слова 'новую') и перед сменой статуса. "
            + "Возвращает {tid:...} для других инструментов.")
    public String findTask(
            @ToolParam(description = "Ключевое слово или часть названия") String keyword,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");
        String kw = keyword.toLowerCase(Locale.ROOT);

        AppUser user = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (user == null || user.getYougileApiKey() == null) return "Задачи не найдены";

        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(telegramUserId).orElse(null);
        if (board == null) return "Задачи не найдены";

        List<YougileColumnDto> columns = yougileClient.getColumns(user.getYougileApiKey(), board.getBoardId());
        if (columns.isEmpty()) {
            columns = Stream.of(board.getColumnTodoId(), board.getColumnInProgressId(),
                            board.getColumnReviewId(), board.getColumnDoneId())
                    .filter(Objects::nonNull).map(id -> new YougileColumnDto(id, id)).toList();
        }

        List<Task> found = yougileClient
                .getTasksByColumns(user.getYougileApiKey(), columns.stream().map(YougileColumnDto::id).toList())
                .stream()
                .map(dto -> mapper.toDomain(dto, board))
                .filter(t -> (t.getTitle() != null && t.getTitle().toLowerCase(Locale.ROOT).contains(kw))
                        || (t.getDescription() != null && t.getDescription().toLowerCase(Locale.ROOT).contains(kw)))
                .toList();

        if (found.isEmpty()) return "Задачи с «" + keyword + "» не найдено.";

        if (found.size() == 1) {
            Task t = found.getFirst();
            agentContextService.rememberTask(telegramUserId, t.getExternalId(), t.getTitle());
        }

        var sb = new StringBuilder();
        for (Task t : found) {
            sb.append("• «").append(t.getTitle()).append("» — ").append(statusLabel(t.getStatus().name()));
            if (t.getDeadline() != null) {
                sb.append(", до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            }
            sb.append(" {tid:").append(t.getExternalId()).append("}\n");
        }
        return sb.toString().trim();
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
