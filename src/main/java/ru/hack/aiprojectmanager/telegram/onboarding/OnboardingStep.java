package ru.hack.aiprojectmanager.telegram.onboarding;

public enum OnboardingStep {
    EMAIL("Введи email от аккаунта YouGile:"),
    PASSWORD("Введи пароль от аккаунта YouGile:"),
    COMPANY_SELECT(null),   // динамический промпт
    BOARD_SELECT(null);     // динамический промпт

    public final String prompt;

    OnboardingStep(String prompt) {
        this.prompt = prompt;
    }
}
