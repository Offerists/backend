package ru.hack.aiprojectmanager.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;

    public record RegistrationData(
            Long telegramId,
            Long chatId,
            String apiKey,
            String companyId,
            String yougileUserId,
            String yougileRole,
            String yougileRealName,
            String yougileEmail,
            String boardId,
            String boardName,
            String columnTodoId,
            String columnInProgressId,
            String columnReviewId,
            String columnDoneId
    ) {}

    @Transactional
    public AppUser register(RegistrationData data) {
        AppUser user = appUserRepository.findFirstByTelegramId(data.telegramId())
                .orElse(AppUser.builder()
                        .telegramId(data.telegramId())
                        .chatId(data.chatId())
                        .build());

        user.setYougileApiKey(data.apiKey());
        user.setYougileUserId(data.yougileUserId());
        user.setYougileCompanyId(data.companyId());
        user.setYougileRole(data.yougileRole());
        user.setYougileRealName(data.yougileRealName());
        user.setYougileEmail(data.yougileEmail());
        AppUser saved = appUserRepository.save(user);

        boardSettingsRepository.clearDefaultForUser(data.telegramId());
        boardSettingsRepository.save(UserBoardSettings.builder()
                .telegramId(data.telegramId())
                .companyId(data.companyId())
                .boardId(data.boardId())
                .boardName(data.boardName())
                .columnTodoId(data.columnTodoId())
                .columnInProgressId(data.columnInProgressId())
                .columnReviewId(data.columnReviewId())
                .columnDoneId(data.columnDoneId())
                .isDefault(true)
                .build());

        log.info("Registered user telegramId={} yougileUserId={} role={}",
                data.telegramId(), data.yougileUserId(), data.yougileRole());
        return saved;
    }
}
