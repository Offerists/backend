package ru.hack.aiprojectmanager.telegram.onboarding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.yougile.YougileAuthClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileBoardDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileCompanyDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettings;
import ru.hack.aiprojectmanager.workspace.WorkspaceSettingsRepository;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final String INTRO = """
            Привет! Чтобы я мог управлять твоими задачами, нужно подключить YouGile.
            Введи логин и пароль — всё остальное настрою сам.

            """;

    private static final String DONE_MESSAGE = """
            Всё настроено! Можешь управлять задачами.
            Напиши что нужно сделать — например: "Создай задачу написать тесты".
            """;

    private static final Map<String, TaskStatus> COLUMN_KEYWORDS = new LinkedHashMap<>();

    static {
        COLUMN_KEYWORDS.put("backlog", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("to do", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("todo", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("новые", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("новая", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("сделать", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("очередь", TaskStatus.TODO);

        COLUMN_KEYWORDS.put("in progress", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("in-progress", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("doing", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("в работе", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("в процессе", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("разработка", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("выполняется", TaskStatus.IN_PROGRESS);

        COLUMN_KEYWORDS.put("review", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("ревью", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("проверка", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("тестирование", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("testing", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("qa", TaskStatus.REVIEW);

        COLUMN_KEYWORDS.put("done", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("completed", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("готово", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("выполнено", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("завершено", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("закрыто", TaskStatus.DONE);
    }

    private final WorkspaceSettingsRepository workspaceRepo;
    private final AppUserRepository appUserRepository;
    private final YougileAuthClient authClient;
    private final YougileClient yougileClient;
    private final ConcurrentHashMap<Long, OnboardingSession> sessions = new ConcurrentHashMap<>();

    public boolean needsOnboarding(Long chatId) {
        return workspaceRepo.findById(chatId)
                .map(s -> s.getYougileApiKey() == null)
                .orElse(true);
    }

    public boolean isInProgress(Long chatId) {
        return sessions.containsKey(chatId);
    }

    public String start(Long chatId, Long telegramId) {
        OnboardingSession session = new OnboardingSession();
        session.setTelegramId(telegramId);
        sessions.put(chatId, session);
        return INTRO + OnboardingStep.EMAIL.prompt;
    }

    public String handle(Long chatId, String input) {
        OnboardingSession session = sessions.computeIfAbsent(chatId, k -> new OnboardingSession());
        return switch (session.getStep()) {
            case EMAIL -> {
                session.setEmail(input.trim());
                session.setStep(OnboardingStep.PASSWORD);
                yield OnboardingStep.PASSWORD.prompt;
            }
            case PASSWORD -> handlePassword(chatId, session, input);
            case COMPANY_SELECT -> handleCompanySelect(chatId, session, input);
            case BOARD_SELECT -> handleBoardSelect(chatId, session, input);
        };
    }

    private String handlePassword(Long chatId, OnboardingSession session, String password) {
        session.setPassword(password);
        try {
            List<YougileCompanyDto> companies = authClient.getCompanies(session.getEmail(), password);
            if (companies.isEmpty()) {
                sessions.remove(chatId);
                return "Компании не найдены. Проверь email и пароль, затем введи /start заново.";
            }
            if (companies.size() == 1) {
                return obtainKeyAndSetup(chatId, session, companies.getFirst());
            }
            session.setCompanies(companies);
            session.setStep(OnboardingStep.COMPANY_SELECT);
            return listPrompt("Найдено несколько компаний. Введи номер нужной:", companies,
                    YougileCompanyDto::displayName);
        } catch (HttpClientErrorException e) {
            log.warn("Auth failed for chatId={}: {}", chatId, e.getStatusCode());
            sessions.remove(chatId);
            return "Неверный логин или пароль. Попробуй снова: /start";
        } catch (Exception e) {
            log.error("Auth error for chatId={}: {}", chatId, e.getMessage());
            sessions.remove(chatId);
            return "Не удалось подключиться к YouGile. Попробуй позже: /start";
        }
    }

    private String handleCompanySelect(Long chatId, OnboardingSession session, String input) {
        List<YougileCompanyDto> companies = session.getCompanies();
        int idx = parseIndex(input, companies.size());
        if (idx < 0) return "Введи номер от 1 до " + companies.size() + ":";
        return obtainKeyAndSetup(chatId, session, companies.get(idx));
    }

    private String handleBoardSelect(Long chatId, OnboardingSession session, String input) {
        List<YougileBoardDto> boards = session.getBoards();
        int idx = parseIndex(input, boards.size());
        if (idx < 0) return "Введи номер от 1 до " + boards.size() + ":";
        return mapColumnsAndComplete(chatId, session, boards.get(idx));
    }

    private String obtainKeyAndSetup(Long chatId, OnboardingSession session, YougileCompanyDto company) {
        try {
            String key = authClient.createApiKey(session.getEmail(), session.getPassword(), company.id());
            if (key == null) {
                sessions.remove(chatId);
                return "Не удалось получить API-ключ. Попробуй снова: /start";
            }
            session.setApiKey(key);
            session.clearCredentials();

            List<YougileBoardDto> boards = yougileClient.getBoards(key);
            if (boards.isEmpty()) {
                sessions.remove(chatId);
                return "В компании «" + company.displayName() + "» не найдено ни одной доски. Создай доску и введи /start.";
            }
            if (boards.size() == 1) {
                return mapColumnsAndComplete(chatId, session, boards.getFirst());
            }
            session.setBoards(boards);
            session.setStep(OnboardingStep.BOARD_SELECT);
            return "Компания «" + company.displayName() + "» подключена.\n\n" +
                    listPrompt("Найдено несколько досок. Введи номер нужной:", boards, YougileBoardDto::displayName);
        } catch (Exception e) {
            log.error("Setup error for chatId={}: {}", chatId, e.getMessage());
            sessions.remove(chatId);
            return "Произошла ошибка при настройке. Попробуй снова: /start";
        }
    }

    private String mapColumnsAndComplete(Long chatId, OnboardingSession session, YougileBoardDto board) {
        try {
            List<YougileColumnDto> columns = yougileClient.getColumns(session.getApiKey(), board.id());
            if (columns.isEmpty()) {
                sessions.remove(chatId);
                return "На доске «" + board.displayName() + "» нет колонок. Выбери другую доску: /start";
            }

            Map<TaskStatus, String> mapping = mapColumns(columns);

            WorkspaceSettings settings = workspaceRepo.findById(chatId)
                    .orElse(WorkspaceSettings.builder().chatId(chatId).build());
            settings.setYougileApiKey(session.getApiKey());
            settings.setColumnTodoId(mapping.get(TaskStatus.TODO));
            settings.setColumnInProgressId(mapping.get(TaskStatus.IN_PROGRESS));
            settings.setColumnReviewId(mapping.get(TaskStatus.REVIEW));
            settings.setColumnDoneId(mapping.get(TaskStatus.DONE));
            workspaceRepo.save(settings);

            linkYougileUserId(chatId, session);

            sessions.remove(chatId);
            return DONE_MESSAGE;
        } catch (Exception e) {
            log.error("Column mapping error for chatId={}: {}", chatId, e.getMessage());
            sessions.remove(chatId);
            return "Не удалось загрузить колонки. Попробуй снова: /start";
        }
    }

    private void linkYougileUserId(Long chatId, OnboardingSession session) {
        if (session.getTelegramId() == null || session.getEmail() == null) return;
        try {
            List<YougileUserDto> users = yougileClient.getUsers(session.getApiKey());
            users.stream()
                    .filter(u -> session.getEmail().equalsIgnoreCase(u.email()))
                    .findFirst()
                    .ifPresent(u -> appUserRepository
                            .findByTelegramIdAndChatId(session.getTelegramId(), chatId)
                            .ifPresent(appUser -> {
                                appUser.setYougileUserId(u.id());
                                appUserRepository.save(appUser);
                                log.info("Linked telegramId={} to yougileUserId={}", session.getTelegramId(), u.id());
                            }));
        } catch (Exception e) {
            log.warn("Could not link YouGile user ID for chatId={}: {}", chatId, e.getMessage());
        }
    }

    private Map<TaskStatus, String> mapColumns(List<YougileColumnDto> columns) {
        Map<TaskStatus, String> mapping = new EnumMap<>(TaskStatus.class);

        for (YougileColumnDto col : columns) {
            String lower = col.title().toLowerCase();
            for (var entry : COLUMN_KEYWORDS.entrySet()) {
                if (lower.contains(entry.getKey()) && !mapping.containsKey(entry.getValue())) {
                    mapping.put(entry.getValue(), col.id());
                    break;
                }
            }
        }

        if (!mapping.containsKey(TaskStatus.TODO)) {
            mapping.put(TaskStatus.TODO, columns.getFirst().id());
        }
        if (!mapping.containsKey(TaskStatus.DONE)) {
            mapping.put(TaskStatus.DONE, columns.getLast().id());
        }
        if (!mapping.containsKey(TaskStatus.IN_PROGRESS)) {
            columns.stream()
                    .filter(c -> !mapping.containsValue(c.id()))
                    .findFirst()
                    .ifPresent(c -> mapping.put(TaskStatus.IN_PROGRESS, c.id()));
        }

        return mapping;
    }

    private int parseIndex(String input, int size) {
        try {
            int idx = Integer.parseInt(input.trim()) - 1;
            return (idx >= 0 && idx < size) ? idx : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private <T> String listPrompt(String header, List<T> items, java.util.function.Function<T, String> nameExtractor) {
        var sb = new StringBuilder(header).append("\n\n");
        for (int i = 0; i < items.size(); i++) {
            sb.append(i + 1).append(". ").append(nameExtractor.apply(items.get(i))).append("\n");
        }
        return sb.toString().trim();
    }
}
