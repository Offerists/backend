package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettings;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettingsRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotUserService {

    private final AppUserRepository appUserRepository;
    private final WorkspaceSettingsRepository workspaceSettingsRepository;

    @Transactional
    public AppUser findOrRegister(Long telegramId, Long chatId, String username, String fullName) {
        return appUserRepository.findByTelegramIdAndChatId(telegramId, chatId)
                .orElseGet(() -> register(telegramId, chatId, username, fullName));
    }

    private AppUser register(Long telegramId, Long chatId, String username, String fullName) {
        workspaceSettingsRepository.findById(chatId)
                .orElseGet(() -> workspaceSettingsRepository.save(
                        WorkspaceSettings.builder().chatId(chatId).build()
                ));

        AppUser user = appUserRepository.save(AppUser.builder()
                .telegramId(telegramId)
                .chatId(chatId)
                .username(username)
                .fullName(fullName)
                .build());

        log.info("Registered new user userId={}, chatId={}", telegramId, chatId);
        return user;
    }
}
