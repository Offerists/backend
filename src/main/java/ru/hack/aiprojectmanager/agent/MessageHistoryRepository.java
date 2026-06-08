package ru.hack.aiprojectmanager.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageHistoryRepository extends JpaRepository<MessageHistory, Long> {

    List<MessageHistory> findTop20ByChatIdOrderByCreatedAtDesc(Long chatId);

    List<MessageHistory> findTop8ByChatIdOrderByCreatedAtDesc(Long chatId);
}
