package ru.hack.aiprojectmanager.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigestScheduler {

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileClient yougileClient;
    private final YougileMapper mapper;
    private final NotificationSender notificationSender;

    @Scheduled(cron = "0 0 9 * * *")
    public void sendMorningReminder() {
        usersWithDigest().forEach(this::sendMorning);
    }

    @Scheduled(cron = "0 0 19 * * *")
    public void sendEveningDigest() {
        usersWithDigest().forEach(this::sendEvening);
    }

    public void sendMorningReminderFor(Long telegramId) {
        appUserRepository.findFirstByTelegramId(telegramId).ifPresent(this::sendMorning);
    }

    public void sendEveningDigestFor(Long telegramId) {
        appUserRepository.findFirstByTelegramId(telegramId).ifPresent(this::sendEvening);
    }

    private List<AppUser> usersWithDigest() {
        return appUserRepository.findAll().stream()
                .filter(u -> u.getYougileApiKey() != null && u.getYougileUserId() != null)
                .filter(u -> Boolean.TRUE.equals(u.getDigestEnabled()))
                .toList();
    }

    private void sendMorning(AppUser user) {
        List<Task> tasks = loadMyActiveTasks(user);
        if (tasks.isEmpty()) return;

        LocalDate today = LocalDate.now();
        List<Task> overdue   = tasks.stream().filter(t -> isOverdue(t, today)).toList();
        List<Task> dueToday  = tasks.stream().filter(t -> isDueOn(t, today)).toList();

        if (overdue.isEmpty() && dueToday.isEmpty()) return;

        StringBuilder sb = new StringBuilder("☀️ Доброе утро!\n\n");
        if (!overdue.isEmpty()) {
            sb.append("🔴 Просрочено (").append(overdue.size()).append("):\n");
            overdue.forEach(t -> sb.append("• «").append(t.getTitle()).append("» — ")
                    .append(ChronoUnit.DAYS.between(t.getDeadline().toLocalDate(), today)).append(" дн.\n"));
            sb.append("\n");
        }
        if (!dueToday.isEmpty()) {
            sb.append("⏰ Сегодня дедлайн (").append(dueToday.size()).append("):\n");
            dueToday.forEach(t -> sb.append("• «").append(t.getTitle()).append("»\n"));
        }

        notificationSender.send(user.getChatId(), sb.toString().trim());
        log.info("Morning reminder → telegramId={}", user.getTelegramId());
    }

    private void sendEvening(AppUser user) {
        List<Task> tasks = loadMyActiveTasks(user);
        if (tasks.isEmpty()) return;

        LocalDate today    = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<Task> overdue     = tasks.stream().filter(t -> isOverdue(t, today)).toList();
        List<Task> dueToday    = tasks.stream().filter(t -> isDueOn(t, today)).toList();
        List<Task> dueTomorrow = tasks.stream().filter(t -> isDueOn(t, tomorrow)).toList();
        List<Task> inReview    = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.REVIEW)
                .filter(t -> !isOverdue(t, today) && !isDueOn(t, today) && !isDueOn(t, tomorrow))
                .toList();
        List<Task> inProgress  = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .filter(t -> !isOverdue(t, today) && !isDueOn(t, today) && !isDueOn(t, tomorrow))
                .toList();

        if (overdue.isEmpty() && dueToday.isEmpty() && dueTomorrow.isEmpty()
                && inReview.isEmpty() && inProgress.isEmpty()) return;

        StringBuilder sb = new StringBuilder("📋 Вечерний дайджест\n\n");

        if (!overdue.isEmpty()) {
            sb.append("🔴 Просрочено (").append(overdue.size()).append("):\n");
            overdue.forEach(t -> sb.append("• «").append(t.getTitle()).append("» — ")
                    .append(ChronoUnit.DAYS.between(t.getDeadline().toLocalDate(), today)).append(" дн.\n"));
            sb.append("\n");
        }
        if (!dueToday.isEmpty()) {
            sb.append("⏰ Сегодня дедлайн:\n");
            dueToday.forEach(t -> sb.append("• «").append(t.getTitle()).append("»\n"));
            sb.append("\n");
        }
        if (!dueTomorrow.isEmpty()) {
            sb.append("📅 Завтра дедлайн:\n");
            dueTomorrow.forEach(t -> sb.append("• «").append(t.getTitle()).append("»\n"));
            sb.append("\n");
        }
        if (!inReview.isEmpty()) {
            sb.append("👀 На проверке:\n");
            inReview.forEach(t -> sb.append("• «").append(t.getTitle()).append("»\n"));
            sb.append("\n");
        }
        if (!inProgress.isEmpty()) {
            sb.append("📌 В работе:\n");
            inProgress.forEach(t -> sb.append("• «").append(t.getTitle()).append("»\n"));
        }

        notificationSender.send(user.getChatId(), sb.toString().trim());
        log.info("Evening digest → telegramId={} ({} tasks)", user.getTelegramId(), tasks.size());
    }

    private List<Task> loadMyActiveTasks(AppUser user) {
        UserBoardSettings board = boardSettingsRepository
                .findByTelegramIdAndIsDefaultTrue(user.getTelegramId())
                .orElse(null);
        if (board == null) return List.of();

        List<String> columnIds = Stream.of(
                        board.getColumnTodoId(), board.getColumnInProgressId(),
                        board.getColumnReviewId(), board.getColumnDoneId())
                .filter(Objects::nonNull)
                .toList();

        return yougileClient.getTasksByColumns(user.getYougileApiKey(), columnIds)
                .stream()
                .map(dto -> mapper.toDomain(dto, board))
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> isAssignedTo(t, user.getYougileUserId()))
                .toList();
    }

    private boolean isAssignedTo(Task task, String yougileUserId) {
        return (task.getAssigneeId() != null && task.getAssigneeId().equals(yougileUserId))
                || (task.getAssigneeIds() != null && task.getAssigneeIds().contains(yougileUserId));
    }

    private boolean isOverdue(Task t, LocalDate today) {
        return t.getDeadline() != null && t.getDeadline().toLocalDate().isBefore(today);
    }

    private boolean isDueOn(Task t, LocalDate date) {
        return t.getDeadline() != null && t.getDeadline().toLocalDate().equals(date);
    }
}
