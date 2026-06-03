package ru.hack.aiprojectmanager.telegram.onboarding;

import lombok.Getter;
import lombok.Setter;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileBoardDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileCompanyDto;

import java.util.List;

@Getter
@Setter
class OnboardingSession {

    private OnboardingStep step = OnboardingStep.EMAIL;

    private Long telegramId;
    private String email;
    private String password;  // временно, очищается после получения ключа
    private List<YougileCompanyDto> companies;
    private List<YougileBoardDto> boards;
    private String apiKey;

    void clearCredentials() {
        password = null;
    }
}
