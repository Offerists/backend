package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.Locale;
import ru.hack.aiprojectmanager.storage.UserBoardSettings;
import ru.hack.aiprojectmanager.storage.UserBoardSettingsRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Component
public class GetUserTasksSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .optional("name", "string", "Имя или @username участника. Без параметра — задачи текущего пользователя.")
            .build();

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileMapper mapper;

    public GetUserTasksSkill(AppUserRepository appUserRepository,
                              UserBoardSettingsRepository boardSettingsRepository,
                              YougileClient yougileClient,
                              YougileMapper mapper) {
        this.appUserRepository = appUserRepository;
        this.boardSettingsRepository = boardSettingsRepository;
        this.yougileClient = yougileClient;
        this.mapper = mapper;
    }

    @Override
    public String getName() {
        return "get_user_tasks";
    }

    @Override
    public String getDescription() {
        return "Получить задачи. Без параметра — задачи текущего пользователя. "
                + "С name — задачи конкретного участника по имени или @username. "
                + "Содержит {tid:...} только для assign_task и update_task_status — не показывай пользователю.";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long telegramUserId, JsonNode args) {
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null || requester.getYougileApiKey() == null) {
            return "Активных задач нет";
        }
        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(telegramUserId).orElse(null);
        if (board == null) {
            return "Активных задач нет";
        }

        String apiKey = requester.getYougileApiKey();

        // Определяем чьи задачи искать
        String name = optText(args, "name");
        String targetYougileId;
        if (name == null) {
            // Без параметра — задачи текущего пользователя
            targetYougileId = requester.getYougileUserId();
        } else {
            // По имени или @username — ищем в компании
            String query = name.startsWith("@") ? name.substring(1).toLowerCase() : name.toLowerCase();
            List<AppUser> candidates = requester.getYougileCompanyId() != null
                    ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                    : List.of();
            targetYougileId = candidates.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(query))
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(query)))
                    .map(AppUser::getYougileUserId)
                    .filter(id -> id != null)
                    .findFirst()
                    .orElse(null);
        }

        // Грузим ВСЕ колонки доски
        List<YougileColumnDto> allColumns = yougileClient.getColumns(apiKey, board.getBoardId());
        if (allColumns.isEmpty()) {
            allColumns = Stream.of(board.getColumnTodoId(), board.getColumnInProgressId(),
                            board.getColumnReviewId(), board.getColumnDoneId())
                    .filter(id -> id != null)
                    .map(id -> new YougileColumnDto(id, id))
                    .toList();
        }

        List<Task> tasks = allColumns.stream()
                .flatMap(col -> {
                    try {
                        return yougileClient.getTasksByColumn(apiKey, col.id()).stream()
                                .map(dto -> mapper.toDomain(dto, board));
                    } catch (Exception ignored) {
                        return Stream.empty();
                    }
                })
                .filter(t -> targetYougileId == null || isAssignedTo(t, targetYougileId))
                .toList();

        if (tasks.isEmpty()) {
            return "Активных задач нет";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            sb.append(i + 1).append(". «").append(t.getTitle()).append("» — ")
                    .append(statusLabel(t.getStatus().name()));
            if (t.getDeadline() != null) {
                sb.append(", до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            }
            // Внутренний идентификатор — только для инструментов, скрыт от пользователя
            sb.append(" {tid:").append(t.getExternalId()).append("}");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private boolean isAssignedTo(Task task, String yougileUserId) {
        return (task.getAssigneeId() != null && task.getAssigneeId().equals(yougileUserId))
                || (task.getAssigneeIds() != null && task.getAssigneeIds().contains(yougileUserId));
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "TODO" -> "К выполнению";
            case "IN_PROGRESS" -> "В работе";
            case "REVIEW" -> "На проверке";
            case "DONE" -> "Готово";
            default -> status;
        };
    }
}
