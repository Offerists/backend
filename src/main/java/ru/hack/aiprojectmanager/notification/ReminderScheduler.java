package ru.hack.aiprojectmanager.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.notification.NotificationSender;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;
import ru.hack.aiprojectmanager.task.TaskEntity;
import ru.hack.aiprojectmanager.task.TaskEntityRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM в HH:mm");

    private final TaskEntityRepository taskRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationSender notificationSender;

    @Scheduled(fixedDelay = 60_000)
    public void sendReminders() {
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(30);
        List<TaskEntity> due = taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(threshold);
        if (due.isEmpty()) return;

        due.forEach(task -> {
            Optional<AppUser> userOpt = appUserRepository.findFirstByTelegramId(task.getTelegramId());
            if (userOpt.isEmpty()) return;
            AppUser user = userOpt.get();

            if (!Boolean.TRUE.equals(user.getRemindersEnabled())) return;

            String text = "⏰ Напоминание: «" + task.getTitle() + "»"
                    + (task.getDeadline() != null
                    ? " — дедлайн " + task.getDeadline().format(FMT)
                    : "");

            notificationSender.send(user.getChatId(), text);
            task.setReminderSentAt(LocalDateTime.now());
            taskRepository.save(task);
            log.info("Reminder sent for task {} to telegramId={}", task.getYougileTaskId(), task.getTelegramId());
        });
    }
}
