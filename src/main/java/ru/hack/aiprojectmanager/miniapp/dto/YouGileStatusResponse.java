package ru.hack.aiprojectmanager.miniapp.dto;

public record YouGileStatusResponse(
        boolean connected,
        String companyId,
        String yougileUserId,
        String role
) {}
