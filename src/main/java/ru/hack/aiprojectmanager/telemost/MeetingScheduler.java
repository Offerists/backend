package ru.hack.aiprojectmanager.telemost;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingScheduler {

    private final ScheduledMeetingRepository meetingRepository;
    private final RecordingService recordingService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void checkScheduledMeetings() {
        List<ScheduledMeeting> due = meetingRepository
                .findByStatusAndScheduledAtBefore(ScheduledMeeting.Status.PENDING, LocalDateTime.now());

        for (ScheduledMeeting meeting : due) {
            log.info("Starting scheduled meeting id={} url={}", meeting.getId(), meeting.getUrl());
            meeting.setStatus(ScheduledMeeting.Status.STARTED);
            meetingRepository.save(meeting);
            recordingService.startRecording(meeting.getChatId(), meeting.getUrl());
        }
    }
}
