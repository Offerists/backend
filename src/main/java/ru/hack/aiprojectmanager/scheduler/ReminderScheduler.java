package ru.hack.aiprojectmanager.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.storage.TaskEntity;
import ru.hack.aiprojectmanager.storage.TaskEntityRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final TaskEntityRepository taskRepository;
    private final TelegramClient telegramClient;

    @Scheduled(cron = "0 0 17 * * *") // каждый день в 17:00
    public void sendDailyDigest() {
        List<TaskEntity> activeTasks = taskRepository.findByStatusNot(TaskStatus.DONE);

        if (activeTasks.isEmpty()) return;

        Map<Long, List<TaskEntity>> byChatId = activeTasks.stream()
                .collect(Collectors.groupingBy(TaskEntity::getChatId));

        byChatId.forEach((chatId, tasks) -> {
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(formatDigest(tasks))
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to send digest to chatId={}: {}", chatId, e.getMessage());
            }
        });
    }

    private String formatDigest(List<TaskEntity> tasks) {
        StringBuilder sb = new StringBuilder("Добрый вечер! Активные задачи на сегодня:\n\n");
        for (TaskEntity task : tasks) {
            sb.append("• ").append(task.getTitle());
            if (task.getDeadline() != null) {
                sb.append(" (дедлайн: ")
                        .append(task.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")))
                        .append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
