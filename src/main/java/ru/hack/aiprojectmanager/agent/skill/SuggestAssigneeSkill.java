package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SuggestAssigneeSkill {

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;

    @Tool(name = "suggest_assignee", description = "Предложить исполнителя на задачу на основе текущей загрузки команды. "
            + "Вызывай когда пользователь спрашивает «кто возьмёт», «кому назначить», «у кого есть время».")
    public String suggestAssignee(
            @Nullable @ToolParam(description = "Название или описание задачи для контекста", required = false) String taskContext,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        if (requester == null || requester.getYougileApiKey() == null) return "Нет доступа к YouGile.";

        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(telegramUserId).orElse(null);
        if (board == null) return "Доска не выбрана.";

        List<YougileUserDto> members = yougileClient.getUsers(requester.getYougileApiKey());
        if (members.isEmpty()) return "Не удалось получить список участников.";

        List<String> activeColumnIds = Stream.of(
                        board.getColumnTodoId(),
                        board.getColumnInProgressId(),
                        board.getColumnReviewId())
                .filter(Objects::nonNull)
                .toList();

        Map<String, Long> loadMap = yougileClient
                .getTasksByColumns(requester.getYougileApiKey(), activeColumnIds)
                .stream()
                .filter(t -> !Boolean.TRUE.equals(t.deleted()) && !Boolean.TRUE.equals(t.archived()))
                .filter(t -> t.assigned() != null)
                .flatMap(t -> t.assigned().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<String> ranked = members.stream()
                .sorted(Comparator.comparingLong(m -> loadMap.getOrDefault(m.id(), 0L)))
                .map(m -> {
                    long count = loadMap.getOrDefault(m.id(), 0L);
                    String name = m.realName() != null ? m.realName() : m.email();
                    return "• " + name + " — " + count + " активн. задач" + (count == 0 ? " ✅" : "");
                })
                .toList();

        return "Загруженность команды:\n" + String.join("\n", ranked)
                + "\n\nРекомендую назначить на " + extractTopName(members, loadMap) + ".";
    }

    private String extractTopName(List<YougileUserDto> members, Map<String, Long> loadMap) {
        return members.stream()
                .min(Comparator.comparingLong(m -> loadMap.getOrDefault(m.id(), 0L)))
                .map(m -> m.realName() != null ? m.realName() : m.email())
                .orElse("кого-то из команды");
    }
}
