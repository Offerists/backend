package ru.hack.aiprojectmanager.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByTelegramIdAndChatId(Long telegramId, Long chatId);

    Optional<AppUser> findFirstByTelegramId(Long telegramId);

    List<AppUser> findByChatId(Long chatId);

    List<AppUser> findByYougileCompanyId(String companyId);
}
