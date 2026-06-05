package ru.hack.aiprojectmanager.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageHistoryRepository extends JpaRepository<MessageHistory, Long> {

    List<MessageHistory> findTop20ByChatIdOrderByCreatedAtDesc(Long chatId);

    List<MessageHistory> findTop8ByChatIdOrderByCreatedAtDesc(Long chatId);
}
