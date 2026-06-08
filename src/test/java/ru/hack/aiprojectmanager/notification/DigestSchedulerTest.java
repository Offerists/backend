package ru.hack.aiprojectmanager.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileMapper;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileTaskDto;
import ru.hack.aiprojectmanager.task.Task;
import ru.hack.aiprojectmanager.task.TaskStatus;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestSchedulerTest {

    @Mock AppUserRepository appUserRepository;
    @Mock UserBoardSettingsRepository boardSettingsRepository;
    @Mock YougileClient yougileClient;
    @Mock YougileMapper mapper;
    @Mock NotificationSender notificationSender;
    @InjectMocks DigestScheduler scheduler;

    private AppUser user;
    private UserBoardSettings board;

    @BeforeEach
    void setUp() {
        user = AppUser.builder()
                .telegramId(42L)
                .chatId(42L)
                .yougileApiKey("key-123")
                .yougileUserId("user-yg-1")
                .digestEnabled(true)
                .remindersEnabled(true)
                .build();

        board = UserBoardSettings.builder()
                .telegramId(42L)
                .boardId("board-1")
                .columnTodoId("col-todo")
                .columnInProgressId("col-progress")
                .columnReviewId("col-review")
                .columnDoneId("col-done")
                .isDefault(true)
                .build();
    }

    @Test
    void sendMorningReminderFor_unknownUser_doesNothing() {
        when(appUserRepository.findFirstByTelegramId(99L)).thenReturn(Optional.empty());

        scheduler.sendMorningReminderFor(99L);

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sendMorningReminderFor_noUrgentTasks_doesNotSend() {
        Task futureTask = task("Будущая задача", TaskStatus.IN_PROGRESS,
                LocalDateTime.now().plusDays(5), "user-yg-1");

        setupMocks(List.of(futureTask));

        scheduler.sendMorningReminderFor(42L);

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sendMorningReminderFor_overdueTask_sendsWithRedFlag() {
        Task overdue = task("Просроченная задача", TaskStatus.IN_PROGRESS,
                LocalDateTime.now().minusDays(2), "user-yg-1");

        setupMocks(List.of(overdue));

        scheduler.sendMorningReminderFor(42L);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(eq(42L), textCaptor.capture());
        assertThat(textCaptor.getValue())
                .contains("🔴")
                .contains("Просроченная задача");
    }

    @Test
    void sendMorningReminderFor_todayDeadline_sendsWithClock() {
        Task todayTask = task("Сегодняшняя задача", TaskStatus.TODO,
                LocalDate.now().atTime(18, 0), "user-yg-1");

        setupMocks(List.of(todayTask));

        scheduler.sendMorningReminderFor(42L);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(eq(42L), textCaptor.capture());
        assertThat(textCaptor.getValue())
                .contains("⏰")
                .contains("Сегодняшняя задача");
    }

    @Test
    void sendMorningReminderFor_taskOfOtherUser_doesNotSend() {
        Task otherUser = task("Чужая задача", TaskStatus.IN_PROGRESS,
                LocalDateTime.now().minusDays(1), "other-user");

        setupMocks(List.of(otherUser));

        scheduler.sendMorningReminderFor(42L);

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sendEveningDigestFor_noTasks_doesNotSend() {
        when(appUserRepository.findFirstByTelegramId(42L)).thenReturn(Optional.of(user));
        when(boardSettingsRepository.findByTelegramIdAndIsDefaultTrue(42L)).thenReturn(Optional.of(board));
        when(yougileClient.getTasksByColumns(any(), any())).thenReturn(List.of());

        scheduler.sendEveningDigestFor(42L);

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sendEveningDigestFor_overdueAndInProgress_groupsCorrectly() {
        Task overdue = task("Просроченная", TaskStatus.IN_PROGRESS,
                LocalDateTime.now().minusDays(1), "user-yg-1");
        Task inProgress = task("В работе", TaskStatus.IN_PROGRESS,
                LocalDateTime.now().plusDays(3), "user-yg-1");

        setupMocks(List.of(overdue, inProgress));

        scheduler.sendEveningDigestFor(42L);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(eq(42L), textCaptor.capture());
        String text = textCaptor.getValue();
        assertThat(text)
                .contains("📋 Вечерний дайджест")
                .contains("🔴")
                .contains("Просроченная")
                .contains("📌")
                .contains("В работе");
    }

    @Test
    void sendEveningDigestFor_tomorrowDeadline_showsTomorrowSection() {
        Task tomorrow = task("Завтрашняя задача", TaskStatus.IN_PROGRESS,
                LocalDate.now().plusDays(1).atTime(12, 0), "user-yg-1");

        setupMocks(List.of(tomorrow));

        scheduler.sendEveningDigestFor(42L);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(eq(42L), textCaptor.capture());
        assertThat(textCaptor.getValue())
                .contains("📅")
                .contains("Завтрашняя задача");
    }

    @Test
    void sendEveningDigestFor_reviewTask_showsReviewSection() {
        Task review = task("На ревью", TaskStatus.REVIEW,
                LocalDateTime.now().plusDays(5), "user-yg-1");

        setupMocks(List.of(review));

        scheduler.sendEveningDigestFor(42L);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).send(eq(42L), textCaptor.capture());
        assertThat(textCaptor.getValue())
                .contains("👀")
                .contains("На ревью");
    }

    private void setupMocks(List<Task> tasks) {
        when(appUserRepository.findFirstByTelegramId(42L)).thenReturn(Optional.of(user));
        when(boardSettingsRepository.findByTelegramIdAndIsDefaultTrue(42L)).thenReturn(Optional.of(board));

        List<YougileTaskDto> dtos = tasks.stream().map(t -> mock(YougileTaskDto.class)).toList();
        when(yougileClient.getTasksByColumns(any(), any())).thenReturn(dtos);

        for (int i = 0; i < dtos.size(); i++) {
            when(mapper.toDomain(eq(dtos.get(i)), any())).thenReturn(tasks.get(i));
        }
    }

    private Task task(String title, TaskStatus status, LocalDateTime deadline, String assigneeId) {
        return Task.builder()
                .title(title)
                .status(status)
                .deadline(deadline)
                .assigneeId(assigneeId)
                .build();
    }
}
