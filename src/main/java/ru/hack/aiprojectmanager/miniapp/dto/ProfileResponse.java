package ru.hack.aiprojectmanager.miniapp.dto;

public record ProfileResponse(
        Long telegramId,
        String username,
        String fullName,
        boolean yougileConnected,
        String yougileRole,
        StatsDto stats
) {
    public record StatsDto(int activeTasks, int doneTasks) {}
}
