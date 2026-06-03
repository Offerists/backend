package ru.hack.aiprojectmanager.telegram.onboarding;

import lombok.Getter;
import lombok.Setter;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileBoardDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileCompanyDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileProjectDto;

import java.util.List;

@Getter
@Setter
class OnboardingSession {

    private OnboardingStep step = OnboardingStep.EMAIL;

    private Long telegramId;
    private Long chatId;
    private String email;
    private String password;

    private List<YougileCompanyDto> companies;
    private List<YougileProjectDto> projects;
    private List<YougileBoardDto> boards;
    private String selectedProjectId;

    // Результаты YouGile auth
    private String apiKey;
    private String companyId;
    private String yougileUserId;
    private String yougileRole;

    void clearCredentials() {
        password = null;
    }
}
