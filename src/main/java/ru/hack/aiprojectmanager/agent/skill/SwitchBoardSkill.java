package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hack.aiprojectmanager.kanban.UserBoardSettings;
import ru.hack.aiprojectmanager.kanban.UserBoardSettingsRepository;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SwitchBoardSkill {

    private final UserBoardSettingsRepository boardSettingsRepository;

    @Tool(name = "switch_board",
          description = "Сменить активную доску. Вызывай когда пользователь хочет переключиться на другую доску "
                  + "или спрашивает какие у него есть доски.")
    @Transactional
    public String switchBoard(
            @ToolParam(description = "Название доски или часть названия для поиска. "
                    + "Передай пустую строку чтобы показать список всех досок.")
            String boardName,
            ToolContext ctx) {

        Long telegramId = (Long) ctx.getContext().get("telegramUserId");
        List<UserBoardSettings> all = boardSettingsRepository.findByTelegramId(telegramId);

        if (all.isEmpty()) {
            return "Нет подключённых досок. Используй /start чтобы добавить.";
        }

        if (boardName == null || boardName.isBlank()) {
            return "Доступные доски:\n" + listBoards(all);
        }

        if (all.size() == 1) {
            return "У тебя только одна доска: «" + all.getFirst().getBoardName() + "».";
        }

        List<UserBoardSettings> matches = all.stream()
                .filter(b -> b.getBoardName() != null
                        && b.getBoardName().toLowerCase().contains(boardName.toLowerCase()))
                .toList();

        if (matches.isEmpty()) {
            return "Доска «" + boardName + "» не найдена. Доступные доски:\n" + listBoards(all);
        }

        if (matches.size() > 1) {
            return "Найдено несколько досок, уточни название:\n" + listBoards(matches);
        }

        UserBoardSettings target = matches.getFirst();
        if (target.isDefault()) {
            return "Доска «" + target.getBoardName() + "» уже активна.";
        }

        boardSettingsRepository.clearDefaultForUser(telegramId);
        target.setDefault(true);
        boardSettingsRepository.save(target);

        return "Переключился на доску «" + target.getBoardName() + "».";
    }

    private String listBoards(List<UserBoardSettings> boards) {
        return boards.stream()
                .map(b -> "• " + displayName(b) + (b.isDefault() ? " (активная)" : ""))
                .collect(Collectors.joining("\n"));
    }

    private String displayName(UserBoardSettings b) {
        return b.getBoardName() != null ? b.getBoardName() : b.getBoardId();
    }
}
