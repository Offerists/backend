package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.storage.UserBoardSettings;
import ru.hack.aiprojectmanager.storage.UserBoardSettingsRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Component
public class FindTaskSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("keyword", "string", "Ключевое слово или часть названия задачи")
            .build();

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileMapper mapper;

    public FindTaskSkill(AppUserRepository appUserRepository,
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
        return "find_task";
    }

    @Override
    public String getDescription() {
        return "Найти задачи по ключевому слову в названии. "
                + "Используй перед созданием задачи и для получения task_id при смене статуса. "
                + "Поиск нечёткий — 'написать тесты' найдёт 'написание тестов'.";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long telegramUserId, JsonNode args) {
        String keyword = requireText(args, "keyword").toLowerCase(Locale.ROOT);

        AppUser user = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (user == null || user.getYougileApiKey() == null) {
            return "Задачи не найдены";
        }

        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(telegramUserId).orElse(null);
        if (board == null) {
            return "Задачи не найдены";
        }

        String apiKey = user.getYougileApiKey();
        List<YougileColumnDto> allColumns = yougileClient.getColumns(apiKey, board.getBoardId());
        if (allColumns.isEmpty()) {
            allColumns = Stream.of(board.getColumnTodoId(), board.getColumnInProgressId(),
                            board.getColumnReviewId(), board.getColumnDoneId())
                    .filter(Objects::nonNull)
                    .map(id -> new YougileColumnDto(id, id))
                    .toList();
        }

        List<Task> found = allColumns.stream()
                .flatMap(col -> {
                    try {
                        return yougileClient.getTasksByColumn(apiKey, col.id()).stream()
                                .map(dto -> mapper.toDomain(dto, board));
                    } catch (Exception e) {
                        log.warn("Failed to fetch tasks for column {}: {}", col.id(), e.getMessage());
                        return Stream.empty();
                    }
                })
                .filter(t -> t.getTitle() != null
                        && t.getTitle().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();

        if (found.isEmpty()) {
            return "Задачи с «" + keyword + "» не найдено.";
        }

        StringBuilder sb = new StringBuilder();
        for (Task t : found) {
            sb.append("• «").append(t.getTitle()).append("» — ").append(statusLabel(t.getStatus().name()));
            if (t.getDeadline() != null) {
                sb.append(", до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            }
            sb.append(" {tid:").append(t.getExternalId()).append("}\n");
        }
        return sb.toString().trim();
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
