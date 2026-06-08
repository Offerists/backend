package ru.hack.aiprojectmanager.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock AppUserRepository appUserRepository;
    @Mock UserBoardSettingsRepository boardSettingsRepository;
    @InjectMocks AppUserService appUserService;

    private static final AppUserService.RegistrationData DATA = new AppUserService.RegistrationData(
            123L, 456L, "api-key", "company-1", "yougile-user-1", "LEAD",
            "board-1", "Sprint Board", "col-todo", "col-wip", "col-review", "col-done"
    );

    @Test
    void register_createsNewUser_whenNotExists() {
        when(appUserRepository.findFirstByTelegramId(123L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appUserService.register(DATA);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        AppUser saved = userCaptor.getValue();

        assertThat(saved.getTelegramId()).isEqualTo(123L);
        assertThat(saved.getYougileApiKey()).isEqualTo("api-key");
        assertThat(saved.getYougileRole()).isEqualTo("LEAD");
        assertThat(saved.getYougileUserId()).isEqualTo("yougile-user-1");
    }

    @Test
    void register_updatesExistingUser_notCreatesNew() {
        AppUser existing = AppUser.builder().telegramId(123L).chatId(456L).yougileApiKey("old-key").build();
        when(appUserRepository.findFirstByTelegramId(123L)).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appUserService.register(DATA);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getYougileApiKey()).isEqualTo("api-key");
    }

    @Test
    void register_clearsOldDefaultBoard_beforeSavingNew() {
        when(appUserRepository.findFirstByTelegramId(any())).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appUserService.register(DATA);

        var inOrder = inOrder(boardSettingsRepository);
        inOrder.verify(boardSettingsRepository).clearDefaultForUser(123L);
        inOrder.verify(boardSettingsRepository).save(any());
    }

    @Test
    void register_savesCorrectBoardSettings() {
        when(appUserRepository.findFirstByTelegramId(any())).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appUserService.register(DATA);

        ArgumentCaptor<UserBoardSettings> captor = ArgumentCaptor.forClass(UserBoardSettings.class);
        verify(boardSettingsRepository).save(captor.capture());
        UserBoardSettings board = captor.getValue();

        assertThat(board.getBoardId()).isEqualTo("board-1");
        assertThat(board.getBoardName()).isEqualTo("Sprint Board");
        assertThat(board.getColumnTodoId()).isEqualTo("col-todo");
        assertThat(board.getColumnDoneId()).isEqualTo("col-done");
        assertThat(board.isDefault()).isTrue();
    }
}
