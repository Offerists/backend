package ru.hack.aiprojectmanager.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserBoardSettingsRepository extends JpaRepository<UserBoardSettings, Long> {

    Optional<UserBoardSettings> findByTelegramIdAndIsDefaultTrue(Long telegramId);

    Optional<UserBoardSettings> findByTelegramIdAndBoardId(Long telegramId, String boardId);

    List<UserBoardSettings> findByTelegramId(Long telegramId);

    @Modifying
    @Transactional
    @Query("update UserBoardSettings u set u.isDefault = false where u.telegramId = :telegramId")
    void clearDefaultForUser(Long telegramId);
}
