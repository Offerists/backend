package ru.hack.aiprojectmanager.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.common.Task;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.notification.NotificationSender;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.storage.UserBoardSettings;
import ru.hack.aiprojectmanager.storage.UserBoardSettingsRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM");

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileMapper mapper;
    private final NotificationSender notificationSender;

    // Вечерний дайджест в 19:00 каждый день
    @Scheduled(cron = "0 0 19 * * *")
    public void sendDailyDigest() {
        List<AppUser> users = appUserRepository.findAll().stream()
                .filter(u -> u.getYougileApiKey() != null && u.getYougileUserId() != null)
                .toList();

        log.info("DigestScheduler: sending digest to {} users", users.size());
        users.forEach(this::sendDigestToUser);
    }

    private void sendDigestToUser(AppUser user) {
        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(user.getTelegramId())
                .orElse(null);
        if (board == null) return;

        List<String> columnIds = Stream.of(
                        board.getColumnTodoId(), board.getColumnInProgressId(),
                        board.getColumnReviewId(), board.getColumnDoneId())
                .filter(Objects::nonNull)
                .toList();

        List<Task> tasks = yougileClient.getTasksByColumns(user.getYougileApiKey(), columnIds)
                .stream()
                .map(dto -> mapper.toDomain(dto, board))
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> isAssignedTo(t, user.getYougileUserId()))
                .toList();

        if (tasks.isEmpty()) return;

        StringBuilder sb = new StringBuilder("📋 Твои активные задачи:\n\n");
        for (Task t : tasks) {
            sb.append("• «").append(t.getTitle()).append("» — ").append(statusLabel(t.getStatus().name()));
            if (t.getDeadline() != null) {
                sb.append(", до ").append(t.getDeadline().format(FMT));
            }
            sb.append("\n");
        }

        notificationSender.send(user.getChatId(), sb.toString().trim());
        log.info("Digest sent to telegramId={} ({} tasks)", user.getTelegramId(), tasks.size());
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
            default -> status;
        };
    }
}
