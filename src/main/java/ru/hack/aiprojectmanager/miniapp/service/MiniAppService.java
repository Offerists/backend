package ru.hack.aiprojectmanager.miniapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.hack.aiprojectmanager.miniapp.MiniAppException;
import ru.hack.aiprojectmanager.miniapp.dto.*;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.kanban.KanbanProvider;
import ru.hack.aiprojectmanager.kanban.yougile.YougileAuthClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileCompanyDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiniAppService {

    private final AppUserRepository userRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileAuthClient authClient;
    private final YougileMapper mapper;
    private final KanbanProvider kanbanProvider;

    // ── Profile ──────────────────────────────────────────────────────────────

    public ProfileResponse getProfile(Long telegramUserId) {
        AppUser user = userRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> MiniAppException.notFound("User not registered. Send /start to the bot first."));

        boolean connected = user.getYougileApiKey() != null;
        ProfileResponse.StatsDto stats = connected ? computeStats(user) : new ProfileResponse.StatsDto(0, 0);

        return new ProfileResponse(
                user.getTelegramId(),
                user.getUsername(),
                user.getFullName(),
                connected,
                user.getYougileRole(),
                stats
        );
    }

    private ProfileResponse.StatsDto computeStats(AppUser user) {
        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(user.getTelegramId())
                .orElse(null);
        if (board == null || user.getYougileUserId() == null) return new ProfileResponse.StatsDto(0, 0);

        List<Task> tasks = yougileClient
                .getTasksByAssignee(user.getYougileApiKey(), user.getYougileUserId())
                .stream()
                .filter(dto -> !Boolean.TRUE.equals(dto.deleted()) && !Boolean.TRUE.equals(dto.archived()))
                .map(dto -> mapper.toDomain(dto, board))
                .toList();

        int active = (int) tasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
        int done = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        return new ProfileResponse.StatsDto(active, done);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    public List<TaskDto> getTasks(Long telegramUserId, String filter) {
        AppUser user = requireConnectedUser(telegramUserId);
        UserBoardSettings board = requireBoard(telegramUserId);

        List<YougileTaskDto> raw = "all".equals(filter)
                ? yougileClient.getTasksByColumns(user.getYougileApiKey(), columnIds(board))
                : yougileClient.getTasksByAssignee(user.getYougileApiKey(), user.getYougileUserId());

        return raw.stream()
                .filter(dto -> !Boolean.TRUE.equals(dto.deleted()) && !Boolean.TRUE.equals(dto.archived()))
                .map(dto -> mapper.toDomain(dto, board))
                .filter(task -> matchesFilter(task, filter, user.getYougileUserId()))
                .map(TaskDto::from)
                .toList();
    }

    private boolean matchesFilter(Task task, String filter, String yougileUserId) {
        return switch (filter == null ? "active" : filter) {
            case "done" -> task.getStatus() == TaskStatus.DONE;
            case "all" -> true;
            default -> task.getStatus() != TaskStatus.DONE;
        };
    }

    // ── YouGile integration ───────────────────────────────────────────────────

    public YouGileStatusResponse getYougileStatus(Long telegramUserId) {
        AppUser user = userRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> MiniAppException.notFound("User not registered."));

        return new YouGileStatusResponse(
                user.getYougileApiKey() != null,
                user.getYougileCompanyId(),
                user.getYougileUserId(),
                user.getYougileRole()
        );
    }

    @Transactional
    public ConnectResponse connectYougile(Long telegramUserId, ConnectRequest req) {
        if (req.getEmail() == null || req.getPassword() == null) {
            throw MiniAppException.badRequest("email and password are required");
        }

        List<YougileCompanyDto> companies;
        try {
            companies = authClient.getCompanies(req.getEmail(), req.getPassword());
        } catch (HttpClientErrorException e) {
            throw MiniAppException.badRequest("Invalid YouGile credentials");
        }

        if (companies.isEmpty()) {
            throw MiniAppException.notFound("No companies found for this account");
        }

        YougileCompanyDto company;
        if (req.getCompanyId() != null) {
            company = companies.stream()
                    .filter(c -> c.id().equals(req.getCompanyId()))
                    .findFirst()
                    .orElseThrow(() -> MiniAppException.badRequest("Company not found: " + req.getCompanyId()));
        } else if (companies.size() == 1) {
            company = companies.getFirst();
        } else {
            List<CompanyDto> list = companies.stream()
                    .map(c -> new CompanyDto(c.id(), c.displayName()))
                    .toList();
            return ConnectResponse.selectCompany(list);
        }

        String apiKey = authClient.createApiKey(req.getEmail(), req.getPassword(), company.id());
        if (apiKey == null) {
            throw new MiniAppException(HttpStatus.BAD_GATEWAY, "Failed to obtain YouGile API key");
        }

        // Persist user with API key
        AppUser user = userRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> MiniAppException.notFound("User not registered. Send /start to the bot first."));
        user.setYougileApiKey(apiKey);
        user.setYougileCompanyId(company.id());

        var me = yougileClient.getCurrentUser(apiKey);
        if (me != null && me.id() != null) {
            user.setYougileUserId(me.id());
            user.setYougileRole(Boolean.TRUE.equals(me.isAdmin()) ? "LEAD" : "MEMBER");
        }
        userRepository.save(user);

        List<BoardDto> boards = getBoardsForUser(user);
        return ConnectResponse.success(boards);
    }

    public List<BoardDto> getBoards(Long telegramUserId) {
        AppUser user = requireConnectedUser(telegramUserId);
        return getBoardsForUser(user);
    }

    private List<BoardDto> getBoardsForUser(AppUser user) {
        List<String> defaultBoardIds = boardSettingsRepository.findByTelegramId(user.getTelegramId())
                .stream()
                .filter(UserBoardSettings::isDefault)
                .map(UserBoardSettings::getBoardId)
                .toList();

        return yougileClient.getBoards(user.getYougileApiKey()).stream()
                .map(b -> new BoardDto(b.id(), b.displayName(), defaultBoardIds.contains(b.id())))
                .toList();
    }

    @Transactional
    public void selectBoard(Long telegramUserId, String boardId) {
        AppUser user = requireConnectedUser(telegramUserId);

        // Verify board exists for this user's API key
        boolean boardExists = yougileClient.getBoards(user.getYougileApiKey()).stream()
                .anyMatch(b -> b.id().equals(boardId));
        if (!boardExists) {
            throw MiniAppException.notFound("Board not found: " + boardId);
        }

        boardSettingsRepository.clearDefaultForUser(telegramUserId);

        UserBoardSettings existing = boardSettingsRepository
                .findByTelegramIdAndBoardId(telegramUserId, boardId)
                .orElse(null);

        String boardName = yougileClient.getBoards(user.getYougileApiKey()).stream()
                .filter(b -> b.id().equals(boardId))
                .map(b -> b.displayName())
                .findFirst().orElse(boardId);

        if (existing != null) {
            existing.setDefault(true);
            existing.setBoardName(boardName);
            boardSettingsRepository.save(existing);
        } else {
            var columns = yougileClient.getColumns(user.getYougileApiKey(), boardId);
            boardSettingsRepository.save(UserBoardSettings.builder()
                    .telegramId(telegramUserId)
                    .companyId(user.getYougileCompanyId())
                    .boardId(boardId)
                    .boardName(boardName)
                    .columnTodoId(columns.isEmpty() ? null : columns.getFirst().id())
                    .columnInProgressId(columns.size() > 1 ? columns.get(1).id() : null)
                    .columnReviewId(columns.size() > 2 ? columns.get(2).id() : null)
                    .columnDoneId(columns.isEmpty() ? null : columns.getLast().id())
                    .isDefault(true)
                    .build());
        }
        log.info("Board {} selected as default for telegramUserId={}", boardId, telegramUserId);
    }

    // ── Task status ───────────────────────────────────────────────────────────

    public TaskDto updateTaskStatus(Long telegramUserId, String taskId, String statusStr) {
        requireConnectedUser(telegramUserId);
        requireBoard(telegramUserId);

        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw MiniAppException.badRequest("Unknown status: " + statusStr
                    + ". Valid values: TODO, IN_PROGRESS, REVIEW, DONE");
        }

        kanbanProvider.moveTask(telegramUserId, taskId, newStatus);

        // Return updated task
        Task updated = kanbanProvider.getTask(telegramUserId, taskId);
        return TaskDto.from(updated);
    }

    // ── Notification settings ─────────────────────────────────────────────────

    public NotificationSettingsResponse getNotificationSettings(Long telegramUserId) {
        AppUser user = userRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> MiniAppException.notFound("User not registered."));
        return new NotificationSettingsResponse(
                Boolean.TRUE.equals(user.getDigestEnabled()),
                Boolean.TRUE.equals(user.getRemindersEnabled())
        );
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(Long telegramUserId, NotificationSettingsRequest req) {
        AppUser user = userRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> MiniAppException.notFound("User not registered."));

        if (req.getDigestEnabled() != null) user.setDigestEnabled(req.getDigestEnabled());
        if (req.getRemindersEnabled() != null) user.setRemindersEnabled(req.getRemindersEnabled());
        userRepository.save(user);

        return new NotificationSettingsResponse(
                Boolean.TRUE.equals(user.getDigestEnabled()),
                Boolean.TRUE.equals(user.getRemindersEnabled())
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AppUser requireConnectedUser(Long telegramUserId) {
        AppUser user = userRepository.findFirstByTelegramId(telegramUserId)
                .orElseThrow(() -> MiniAppException.notFound("User not registered."));
        if (user.getYougileApiKey() == null) {
            throw MiniAppException.conflict("YouGile not connected. Complete onboarding first.");
        }
        return user;
    }

    private UserBoardSettings requireBoard(Long telegramUserId) {
        return boardSettingsRepository.findByTelegramIdAndIsDefaultTrue(telegramUserId)
                .orElseThrow(() -> MiniAppException.conflict("No board selected. Select a board first."));
    }

    private List<String> columnIds(UserBoardSettings board) {
        return Stream.of(
                        board.getColumnTodoId(),
                        board.getColumnInProgressId(),
                        board.getColumnReviewId(),
                        board.getColumnDoneId())
                .filter(Objects::nonNull)
                .toList();
    }
}
