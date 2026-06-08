package ru.hack.aiprojectmanager.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hack.aiprojectmanager.task.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskEntityRepository extends JpaRepository<TaskEntity, Long> {
    Optional<TaskEntity> findByYougileTaskIdAndTelegramId(String yougileTaskId, Long telegramId);

    List<TaskEntity> findByDeadlineBeforeAndReminderSentAtIsNull(LocalDateTime threshold);

    List<TaskEntity> findByStatusNot(TaskStatus taskStatus);
}
