package ru.hack.aiprojectmanager.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import ru.hack.aiprojectmanager.telemost.ScheduledMeeting;
import ru.hack.aiprojectmanager.telemost.ScheduledMeetingRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleMeetingSkillTest {

    @Mock ScheduledMeetingRepository meetingRepository;
    @InjectMocks ScheduleMeetingSkill skill;

    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ToolContext(Map.of("telegramUserId", 1L, "chatId", -100L));
    }

    @Test
    void rejectsNonTelemostUrl() {
        String result = skill.scheduleMeeting("https://zoom.us/j/123", futureDate(), ctx);

        assertThat(result).contains("Ссылка не похожа");
        verifyNoInteractions(meetingRepository);
    }

    @Test
    void rejectsPastDate() {
        String result = skill.scheduleMeeting(
                "https://telemost.yandex.ru/j/abc",
                "2020-01-01T10:00",
                ctx
        );

        assertThat(result).contains("уже прошло");
        verifyNoInteractions(meetingRepository);
    }

    @Test
    void rejectsUnparsableDate() {
        String result = skill.scheduleMeeting(
                "https://telemost.yandex.ru/j/abc",
                "завтра в 15:00",
                ctx
        );

        assertThat(result).contains("Не удалось распознать");
        verifyNoInteractions(meetingRepository);
    }

    @Test
    void savesValidMeeting() {
        String url = "https://telemost.yandex.ru/j/abc123";
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = skill.scheduleMeeting(url, futureDate(), ctx);

        assertThat(result).contains("запланирована");
        ArgumentCaptor<ScheduledMeeting> captor = ArgumentCaptor.forClass(ScheduledMeeting.class);
        verify(meetingRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo(url);
        assertThat(captor.getValue().getChatId()).isEqualTo(-100L);
        assertThat(captor.getValue().getStatus()).isEqualTo(ScheduledMeeting.Status.PENDING);
    }

    private static String futureDate() {
        return LocalDateTime.now().plusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }
}
