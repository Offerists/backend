package ru.hack.aiprojectmanager.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotUserService {

    private final AppUserRepository appUserRepository;

    @Transactional
    public AppUser findOrRegister(Long telegramId, Long chatId, String username, String fullName) {
        return appUserRepository.findFirstByTelegramId(telegramId)
                .orElseGet(() -> {
                    AppUser user = appUserRepository.save(AppUser.builder()
                            .telegramId(telegramId)
                            .chatId(chatId)
                            .username(username)
                            .fullName(fullName)
                            .build());
                    log.info("Registered new user telegramId={}", telegramId);
                    return user;
                });
    }
}
