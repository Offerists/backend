package ru.hack.aiprojectmanager.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByTelegramIdAndChatId(Long telegramId, Long chatId);

    Optional<AppUser> findFirstByTelegramId(Long telegramId);

    List<AppUser> findByChatId(Long chatId);

    List<AppUser> findByYougileCompanyId(String companyId);
}
