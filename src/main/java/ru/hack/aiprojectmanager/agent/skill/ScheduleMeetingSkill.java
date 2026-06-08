package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.telemost.ScheduledMeeting;
import ru.hack.aiprojectmanager.telemost.ScheduledMeetingRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ScheduleMeetingSkill {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("d MMMM yyyy в HH:mm", new Locale("ru"));

    private final ScheduledMeetingRepository meetingRepository;

    @Tool(name = "schedule_meeting_recording",
          description = "Запланировать автоматическую запись Telemost-встречи на указанное время. "
                  + "Вызывай когда пользователь хочет записать будущую встречу по ссылке.")
    public String scheduleMeeting(
            @ToolParam(description = "Полная ссылка на Telemost-встречу (https://telemost.yandex.ru/j/...)")
            String url,
            @ToolParam(description = "Дата и время начала записи, формат ISO-8601: yyyy-MM-ddTHH:mm")
            String scheduledAt,
            ToolContext ctx) {

        Long telegramId = (Long) ctx.getContext().get("telegramUserId");
        Long chatId = (Long) ctx.getContext().get("chatId");

        if (!url.contains("telemost.yandex.ru/j/")) {
            return "Ссылка не похожа на Telemost-встречу. Проверь URL.";
        }

        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(scheduledAt);
        } catch (Exception e) {
            return "Не удалось распознать дату. Используй формат: 2024-03-15T15:00";
        }

        if (dateTime.isBefore(LocalDateTime.now())) {
            return "Указанное время уже прошло.";
        }

        meetingRepository.save(ScheduledMeeting.builder()
                .chatId(chatId)
                .telegramId(telegramId)
                .url(url)
                .scheduledAt(dateTime)
                .build());

        return "Запись встречи запланирована на " + dateTime.format(DISPLAY) + ". "
                + "Бот автоматически подключится и запишет встречу.";
    }
}
