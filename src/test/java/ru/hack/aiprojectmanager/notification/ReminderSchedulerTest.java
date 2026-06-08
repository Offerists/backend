package ru.hack.aiprojectmanager.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hack.aiprojectmanager.task.TaskEntity;
import ru.hack.aiprojectmanager.task.TaskEntityRepository;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock TaskEntityRepository taskRepository;
    @Mock AppUserRepository appUserRepository;
    @Mock NotificationSender notificationSender;
    @InjectMocks ReminderScheduler scheduler;

    @Test
    void sendReminders_noTasksDue_doesNothing() {
        when(taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(any())).thenReturn(List.of());

        scheduler.sendReminders();

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sendReminders_dueSoonTask_sendsNotification() {
        TaskEntity task = taskEntity("Написать тесты", LocalDateTime.now().plusMinutes(15));
        AppUser user = userWithReminders(true);

        when(taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(any())).thenReturn(List.of(task));
        when(appUserRepository.findFirstByTelegramId(42L)).thenReturn(Optional.of(user));

        scheduler.sendReminders();

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(eq(100L), textCaptor.capture());
        assertThat(textCaptor.getValue())
                .contains("⏰")
                .contains("Написать тесты");
    }

    @Test
    void sendReminders_marksReminderSentAt() {
        TaskEntity task = taskEntity("Задача с дедлайном", LocalDateTime.now().plusMinutes(10));
        AppUser user = userWithReminders(true);

        when(taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(any())).thenReturn(List.of(task));
        when(appUserRepository.findFirstByTelegramId(42L)).thenReturn(Optional.of(user));

        scheduler.sendReminders();

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getReminderSentAt()).isNotNull();
    }

    @Test
    void sendReminders_remindersDisabled_skipsNotification() {
        TaskEntity task = taskEntity("Задача", LocalDateTime.now().plusMinutes(10));
        AppUser user = userWithReminders(false);

        when(taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(any())).thenReturn(List.of(task));
        when(appUserRepository.findFirstByTelegramId(42L)).thenReturn(Optional.of(user));

        scheduler.sendReminders();

        verifyNoInteractions(notificationSender);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void sendReminders_userNotFound_skipsTask() {
        TaskEntity task = taskEntity("Задача", LocalDateTime.now().plusMinutes(10));

        when(taskRepository.findByDeadlineBeforeAndReminderSentAtIsNull(any())).thenReturn(List.of(task));
        when(appUserRepository.findFirstByTelegramId(42L)).thenReturn(Optional.empty());

        scheduler.sendReminders();

        verifyNoInteractions(notificationSender);
    }

    private TaskEntity taskEntity(String title, LocalDateTime deadline) {
        return TaskEntity.builder()
                .yougileTaskId("task-" + title.hashCode())
                .telegramId(42L)
                .title(title)
                .status(TaskStatus.IN_PROGRESS)
                .deadline(deadline)
                .build();
    }

    private AppUser userWithReminders(boolean enabled) {
        return AppUser.builder()
                .telegramId(42L)
                .chatId(100L)
                .remindersEnabled(enabled)
                .build();
    }
}
