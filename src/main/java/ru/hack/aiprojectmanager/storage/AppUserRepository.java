package ru.hack.aiprojectmanager.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByTelegramIdAndChatId(Long telegramId, Long chatId);
}
