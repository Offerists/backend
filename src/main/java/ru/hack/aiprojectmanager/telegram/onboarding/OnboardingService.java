package ru.hack.aiprojectmanager.telegram.onboarding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.hack.aiprojectmanager.common.TaskStatus;
import ru.hack.aiprojectmanager.kanban.yougile.YougileAuthClient;
import ru.hack.aiprojectmanager.kanban.yougile.YougileClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileBoardDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileColumnDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileCompanyDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileProjectDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileUserDto;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;
import ru.hack.aiprojectmanager.storage.UserBoardSettings;
import ru.hack.aiprojectmanager.storage.UserBoardSettingsRepository;

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
            Привет! Введи логин и пароль от YouGile — я автоматически подключу твой аккаунт.

            """;

    private static final Map<String, TaskStatus> COLUMN_KEYWORDS = new LinkedHashMap<>();

    static {
        COLUMN_KEYWORDS.put("backlog", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("to do", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("todo", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("новые", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("сделать", TaskStatus.TODO);
        COLUMN_KEYWORDS.put("in progress", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("в работе", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("разработка", TaskStatus.IN_PROGRESS);
        COLUMN_KEYWORDS.put("review", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("проверка", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("тестирование", TaskStatus.REVIEW);
        COLUMN_KEYWORDS.put("done", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("готово", TaskStatus.DONE);
        COLUMN_KEYWORDS.put("завершено", TaskStatus.DONE);
    }

    private final AppUserRepository appUserRepository;
    private final UserBoardSettingsRepository boardSettingsRepository;
    private final YougileAuthClient authClient;
    private final YougileClient yougileClient;
    private final ConcurrentHashMap<Long, OnboardingSession> sessions = new ConcurrentHashMap<>();

    public boolean needsOnboarding(Long telegramUserId) {
        return appUserRepository.findFirstByTelegramId(telegramUserId)
                .map(u -> u.getYougileApiKey() == null
                        || boardSettingsRepository.findByTelegramIdAndIsDefaultTrue(telegramUserId).isEmpty())
                .orElse(true);
    }

    public boolean isInProgress(Long telegramUserId) {
        return sessions.containsKey(telegramUserId);
    }

    public String start(Long chatId, Long telegramUserId) {
        OnboardingSession session = new OnboardingSession();
        session.setTelegramId(telegramUserId);
        session.setChatId(chatId);
        sessions.put(telegramUserId, session);
        return INTRO + OnboardingStep.EMAIL.prompt;
    }

    public String handle(Long telegramUserId, String input) {
        OnboardingSession session = sessions.computeIfAbsent(telegramUserId, k -> new OnboardingSession());
        return switch (session.getStep()) {
            case EMAIL -> {
                session.setEmail(input.trim());
                session.setStep(OnboardingStep.PASSWORD);
                yield OnboardingStep.PASSWORD.prompt;
            }
            case PASSWORD -> handlePassword(telegramUserId, session, input);
            case COMPANY_SELECT -> handleCompanySelect(telegramUserId, session, input);
            case PROJECT_SELECT -> handleProjectSelect(telegramUserId, session, input);
            case BOARD_SELECT -> handleBoardSelect(telegramUserId, session, input);
        };
    }

    private String handlePassword(Long telegramUserId, OnboardingSession session, String password) {
        session.setPassword(password);
        try {
            List<YougileCompanyDto> companies = authClient.getCompanies(session.getEmail(), password);
            if (companies.isEmpty()) {
                session.setStep(OnboardingStep.EMAIL);
                session.setPassword(null);
                return "Компании не найдены. Проверь email и попробуй снова.\n\n" + OnboardingStep.EMAIL.prompt;
            }
            if (companies.size() == 1) {
                return obtainKeyAndSetup(telegramUserId, session, companies.getFirst());
            }
            session.setCompanies(companies);
            session.setStep(OnboardingStep.COMPANY_SELECT);
            return listPrompt("Найдено несколько компаний. Введи номер нужной:", companies,
                    YougileCompanyDto::displayName);
        } catch (HttpClientErrorException e) {
            log.warn("Auth failed for telegramUserId={}: {}", telegramUserId, e.getStatusCode());
            session.setStep(OnboardingStep.PASSWORD);
            return "Неверный логин или пароль. Попробуй снова:\n\n" + OnboardingStep.PASSWORD.prompt;
        } catch (Exception e) {
            log.error("Auth error for telegramUserId={}: {}", telegramUserId, e.getMessage());
            sessions.remove(telegramUserId);
            return "Не удалось подключиться к YouGile. Напиши /start чтобы попробовать снова.";
        }
    }

    private String handleCompanySelect(Long telegramUserId, OnboardingSession session, String input) {
        List<YougileCompanyDto> companies = session.getCompanies();
        int idx = parseIndex(input, companies.size());
        if (idx < 0) return "Введи номер от 1 до " + companies.size() + ":";
        return obtainKeyAndSetup(telegramUserId, session, companies.get(idx));
    }

    private String handleProjectSelect(Long telegramUserId, OnboardingSession session, String input) {
        List<YougileProjectDto> projects = session.getProjects();
        int idx = parseIndex(input, projects.size());
        if (idx < 0) return "Введи номер от 1 до " + projects.size() + ":";
        YougileProjectDto project = projects.get(idx);
        session.setSelectedProjectId(project.id());
        return loadBoards(telegramUserId, session, project.displayName());
    }

    private String handleBoardSelect(Long telegramUserId, OnboardingSession session, String input) {
        List<YougileBoardDto> boards = session.getBoards();
        int idx = parseIndex(input, boards.size());
        if (idx < 0) return "Введи номер от 1 до " + boards.size() + ":";
        return mapColumnsAndComplete(telegramUserId, session, boards.get(idx));
    }

    private String obtainKeyAndSetup(Long telegramUserId, OnboardingSession session, YougileCompanyDto company) {
        try {
            String apiKey = authClient.createApiKey(session.getEmail(), session.getPassword(), company.id());
            if (apiKey == null) {
                sessions.remove(telegramUserId);
                return "Не удалось получить API-ключ. Напиши /start чтобы попробовать снова.";
            }
            session.setApiKey(apiKey);
            session.setCompanyId(company.id());

            // Получаем YouGile user ID и роль для этого пользователя
            linkYougileUser(telegramUserId, session, apiKey);

            session.clearCredentials();

            List<YougileProjectDto> projects = yougileClient.getProjects(apiKey);
            if (projects.isEmpty()) {
                return loadBoards(telegramUserId, session, company.displayName());
            }
            if (projects.size() == 1) {
                session.setSelectedProjectId(projects.getFirst().id());
                return loadBoards(telegramUserId, session, projects.getFirst().displayName());
            }
            session.setProjects(projects);
            session.setStep(OnboardingStep.PROJECT_SELECT);
            return "Компания «" + company.displayName() + "» подключена.\n\n" +
                    listPrompt("Выбери проект:", projects, YougileProjectDto::displayName);
        } catch (Exception e) {
            log.error("Setup error for telegramUserId={}: {}", telegramUserId, e.getMessage());
            sessions.remove(telegramUserId);
            return "Ошибка при настройке. Напиши /start чтобы попробовать снова.";
        }
    }

    private void linkYougileUser(Long telegramUserId, OnboardingSession session, String apiKey) {
        try {
            List<YougileUserDto> users = yougileClient.getUsers(apiKey);
            users.stream()
                    .filter(u -> session.getEmail().equalsIgnoreCase(u.email()))
                    .findFirst()
                    .ifPresent(u -> session.setYougileUserId(u.id()));
        } catch (Exception e) {
            log.warn("Could not fetch YouGile user info: {}", e.getMessage());
        }
    }

    private String loadBoards(Long telegramUserId, OnboardingSession session, String contextName) {
        try {
            List<YougileBoardDto> boards = session.getSelectedProjectId() != null
                    ? yougileClient.getBoardsByProject(session.getApiKey(), session.getSelectedProjectId())
                    : yougileClient.getBoards(session.getApiKey());

            if (boards.isEmpty()) {
                sessions.remove(telegramUserId);
                return "В «" + contextName + "» нет досок. Создай доску и напиши /start.";
            }
            if (boards.size() == 1) {
                return mapColumnsAndComplete(telegramUserId, session, boards.getFirst());
            }
            session.setBoards(boards);
            session.setStep(OnboardingStep.BOARD_SELECT);
            return listPrompt("Выбери доску в «" + contextName + "»:", boards, YougileBoardDto::displayName);
        } catch (Exception e) {
            log.error("Board load error for telegramUserId={}: {}", telegramUserId, e.getMessage());
            sessions.remove(telegramUserId);
            return "Не удалось загрузить доски. Напиши /start чтобы попробовать снова.";
        }
    }

    private String mapColumnsAndComplete(Long telegramUserId, OnboardingSession session, YougileBoardDto board) {
        try {
            List<YougileColumnDto> columns = yougileClient.getColumns(session.getApiKey(), board.id());
            if (columns.isEmpty()) {
                sessions.remove(telegramUserId);
                return "На доске «" + board.displayName() + "» нет колонок. Выбери другую доску: /start";
            }

            Map<TaskStatus, String> mapping = mapColumns(columns);
            boolean confident = isConfidentMapping(mapping, columns.size());

            // Сохраняем пользователя
            AppUser user = appUserRepository.findFirstByTelegramId(telegramUserId)
                    .orElse(AppUser.builder()
                            .telegramId(telegramUserId)
                            .chatId(session.getChatId())
                            .build());
            user.setYougileApiKey(session.getApiKey());
            user.setYougileUserId(session.getYougileUserId());
            user.setYougileCompanyId(session.getCompanyId());
            user.setYougileRole(resolveRole(telegramUserId, session.getCompanyId()));
            appUserRepository.save(user);

            // Сохраняем настройки доски как default
            boardSettingsRepository.clearDefaultForUser(telegramUserId);
            boardSettingsRepository.save(UserBoardSettings.builder()
                    .telegramId(telegramUserId)
                    .companyId(session.getCompanyId())
                    .boardId(board.id())
                    .columnTodoId(mapping.get(TaskStatus.TODO))
                    .columnInProgressId(mapping.get(TaskStatus.IN_PROGRESS))
                    .columnReviewId(mapping.get(TaskStatus.REVIEW))
                    .columnDoneId(mapping.get(TaskStatus.DONE))
                    .isDefault(true)
                    .build());

            sessions.remove(telegramUserId);

            String doneMsg = "Готово! Доска «" + board.displayName() + "» подключена.\n\n";
            if (!confident) {
                doneMsg += "Определил колонки автоматически:\n" + formatMapping(mapping, columns) + "\n"
                        + "Если что-то не так — скажи мне.\n\n";
            }
            doneMsg += "Теперь просто пиши что нужно сделать!";
            return doneMsg;
        } catch (Exception e) {
            log.error("Column mapping error for telegramUserId={}: {}", telegramUserId, e.getMessage());
            sessions.remove(telegramUserId);
            return "Не удалось загрузить колонки. Напиши /start чтобы попробовать снова.";
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
        if (!mapping.containsKey(TaskStatus.TODO)) mapping.put(TaskStatus.TODO, columns.getFirst().id());
        if (!mapping.containsKey(TaskStatus.DONE)) mapping.put(TaskStatus.DONE, columns.getLast().id());
        if (!mapping.containsKey(TaskStatus.IN_PROGRESS)) {
            columns.stream().filter(c -> !mapping.containsValue(c.id())).findFirst()
                    .ifPresent(c -> mapping.put(TaskStatus.IN_PROGRESS, c.id()));
        }
        return mapping;
    }

    // Считаем маппинг уверенным если хотя бы TODO, IN_PROGRESS и DONE нашлись по имени
    private boolean isConfidentMapping(Map<TaskStatus, String> mapping, int totalColumns) {
        return mapping.containsKey(TaskStatus.TODO)
                && mapping.containsKey(TaskStatus.IN_PROGRESS)
                && mapping.containsKey(TaskStatus.DONE)
                && totalColumns >= 2;
    }

    private String formatMapping(Map<TaskStatus, String> mapping, List<YougileColumnDto> columns) {
        Map<String, String> idToName = new java.util.HashMap<>();
        columns.forEach(c -> idToName.put(c.id(), c.title()));
        StringBuilder sb = new StringBuilder();
        for (var entry : mapping.entrySet()) {
            String label = switch (entry.getKey()) {
                case TODO -> "К выполнению";
                case IN_PROGRESS -> "В работе";
                case REVIEW -> "На проверке";
                case DONE -> "Готово";
            };
            sb.append("• ").append(label).append(" → «")
              .append(idToName.getOrDefault(entry.getValue(), entry.getValue())).append("»\n");
        }
        return sb.toString().trim();
    }

    private String resolveRole(Long telegramUserId, String companyId) {
        boolean isFirst = appUserRepository.findByYougileCompanyId(companyId).stream()
                .filter(u -> !u.getTelegramId().equals(telegramUserId))
                .noneMatch(u -> u.getYougileApiKey() != null);
        return isFirst ? "LEAD" : "MEMBER";
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
