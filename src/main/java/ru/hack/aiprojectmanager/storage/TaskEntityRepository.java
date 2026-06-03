package ru.hack.aiprojectmanager.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskEntityRepository extends JpaRepository<TaskEntity, Long> {

    Optional<TaskEntity> findByYougileTaskIdAndChatId(String yougileTaskId, Long chatId);

    List<TaskEntity> findByDeadlineBeforeAndReminderSentAtIsNull(LocalDateTime threshold);
}
