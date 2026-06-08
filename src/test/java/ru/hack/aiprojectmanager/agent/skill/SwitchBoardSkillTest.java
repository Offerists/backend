package ru.hack.aiprojectmanager.agent.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwitchBoardSkillTest {

    @Mock UserBoardSettingsRepository boardSettingsRepository;
    @InjectMocks SwitchBoardSkill skill;

    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ToolContext(Map.of("telegramUserId", 42L, "chatId", 42L));
    }

    @Test
    void noBoards_returnsStartPrompt() {
        when(boardSettingsRepository.findByTelegramId(42L)).thenReturn(List.of());

        String result = skill.switchBoard("Sprint", ctx);

        assertThat(result).contains("/start");
        verify(boardSettingsRepository, never()).save(any());
    }

    @Test
    void onlyOneBoard_informsUser() {
        when(boardSettingsRepository.findByTelegramId(42L))
                .thenReturn(List.of(board("board-1", "Sprint", true)));

        String result = skill.switchBoard("Sprint", ctx);

        assertThat(result).contains("только одна доска");
        verify(boardSettingsRepository, never()).save(any());
    }

    @Test
    void emptyQuery_listsAllBoards() {
        when(boardSettingsRepository.findByTelegramId(42L))
                .thenReturn(List.of(board("b1", "Sprint", true), board("b2", "Backlog", false)));

        String result = skill.switchBoard("", ctx);

        assertThat(result).contains("Sprint").contains("Backlog").contains("активная");
        verify(boardSettingsRepository, never()).save(any());
    }

    @Test
    void noMatch_showsAvailableBoards() {
        when(boardSettingsRepository.findByTelegramId(42L))
                .thenReturn(List.of(board("b1", "Sprint", true), board("b2", "Backlog", false)));

        String result = skill.switchBoard("Marketing", ctx);

        assertThat(result).contains("не найдена").contains("Sprint").contains("Backlog");
        verify(boardSettingsRepository, never()).save(any());
    }

    @Test
    void multipleMatches_asksToRefine() {
        when(boardSettingsRepository.findByTelegramId(42L)).thenReturn(List.of(
                board("b1", "Sprint 1", true),
                board("b2", "Sprint 2", false),
                board("b3", "Backlog", false)
        ));

        String result = skill.switchBoard("Sprint", ctx);

        assertThat(result).contains("уточни").contains("Sprint 1").contains("Sprint 2");
        verify(boardSettingsRepository, never()).save(any());
    }

    @Test
    void targetAlreadyActive_informsUser() {
        when(boardSettingsRepository.findByTelegramId(42L))
                .thenReturn(List.of(board("b1", "Sprint", true), board("b2", "Backlog", false)));

        String result = skill.switchBoard("Sprint", ctx);

        assertThat(result).contains("уже активна");
        verify(boardSettingsRepository, never()).save(any());
    }

    @Test
    void successfulSwitch_clearsOldAndSetsNew() {
        UserBoardSettings sprint = board("b1", "Sprint", true);
        UserBoardSettings backlog = board("b2", "Backlog", false);
        when(boardSettingsRepository.findByTelegramId(42L)).thenReturn(List.of(sprint, backlog));

        String result = skill.switchBoard("Backlog", ctx);

        assertThat(result).contains("Backlog");
        verify(boardSettingsRepository).clearDefaultForUser(42L);
        verify(boardSettingsRepository).save(argThat(b -> b.getBoardId().equals("b2") && b.isDefault()));
    }

    private UserBoardSettings board(String id, String name, boolean isDefault) {
        return UserBoardSettings.builder()
                .boardId(id)
                .boardName(name)
                .telegramId(42L)
                .companyId("company-1")
                .isDefault(isDefault)
                .build();
    }
}
