package ru.hack.aiprojectmanager.kanban.yougile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class YougileProvider implements KanbanProvider {

    private final YougileClient client;
    private final YougileMapper mapper;
    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;

    @Override
    public String createTask(Long telegramUserId, Task task) {
        var ctx = loadContext(telegramUserId);
        var created = client.createTask(ctx.user().getYougileApiKey(), mapper.toRequest(task, ctx.board()));
        return created.id();
    }

    @Override
    public void moveTask(Long telegramUserId, String externalTaskId, TaskStatus newStatus) {
        var ctx = loadContext(telegramUserId);
        client.updateTask(ctx.user().getYougileApiKey(), externalTaskId,
                mapper.toMoveRequest(newStatus, ctx.board()));
    }

    @Override
    public List<Task> getTasksByAssignee(Long telegramUserId, String yougileAssigneeId) {
        var ctx = loadContext(telegramUserId);
        // assignedTo filter — один запрос вместо N по колонкам
        return client.getTasksByAssignee(ctx.user().getYougileApiKey(), yougileAssigneeId)
                .stream()
                .map(dto -> mapper.toDomain(dto, ctx.board()))
                .toList();
    }

    @Override
    public Task getTask(Long telegramUserId, String externalTaskId) {
        var ctx = loadContext(telegramUserId);
        return mapper.toDomain(client.getTask(ctx.user().getYougileApiKey(), externalTaskId), ctx.board());
    }

    @Override
    public void updateTask(Long telegramUserId, String externalTaskId, Task updated) {
        var ctx = loadContext(telegramUserId);
        client.updateTask(ctx.user().getYougileApiKey(), externalTaskId,
                mapper.toRequest(updated, ctx.board()));
    }

    private UserContext loadContext(Long telegramUserId) {
        AppUser user = appUserRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Пользователь не зарегистрирован: " + telegramUserId));
        if (user.getYougileApiKey() == null) {
            throw new IllegalStateException(
                    "YouGile не настроен для пользователя " + telegramUserId + ". Введи /start");
        }
        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(telegramUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Не выбрана доска для пользователя " + telegramUserId));
        return new UserContext(user, board);
    }

    private record UserContext(AppUser user, UserBoardSettings board) {}
}
