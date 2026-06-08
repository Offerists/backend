package ru.hack.aiprojectmanager.telemost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledMeetingRepository extends JpaRepository<ScheduledMeeting, Long> {

    List<ScheduledMeeting> findByStatusAndScheduledAtBefore(ScheduledMeeting.Status status, LocalDateTime now);
}
