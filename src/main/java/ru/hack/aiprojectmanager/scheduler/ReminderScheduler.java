package ru.hack.aiprojectmanager.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.notification.NotificationSender;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.storage.TaskEntity;
import ru.hack.aiprojectmanager.storage.TaskEntityRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM в HH:mm");

    private final TaskEntityRepository taskRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationSender notificationSender;

    // Каждую минуту проверяем задачи с дедлайном в ближайшие 30 минут
    @Scheduled(fixedDelay = 60_000)
    public void sendReminders() {
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(30);
        List<TaskEntity> due = taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(threshold);
        if (due.isEmpty()) return;

        due.forEach(task -> {
            Long chatId = resolveChatId(task);
            if (chatId == null) return;

            String text = "⏰ Напоминание: «" + task.getTitle() + "»"
                    + (task.getDeadline() != null
                    ? " — дедлайн " + task.getDeadline().format(FMT)
                    : "");

            notificationSender.send(chatId, text);
            task.setReminderSentAt(LocalDateTime.now());
            taskRepository.save(task);
            log.info("Reminder sent for task {} to chatId={}", task.getYougileTaskId(), chatId);
        });
    }

    private Long resolveChatId(TaskEntity task) {
        // chatId в TaskEntity = telegramId пользователя (приватный чат)
        if (task.getChatId() != null) {
            return task.getChatId();
        }
        if (task.getAssigneeId() != null) {
            return appUserRepository.findById(task.getAssigneeId())
                    .map(u -> u.getChatId())
                    .orElse(null);
        }
        return null;
    }
}
