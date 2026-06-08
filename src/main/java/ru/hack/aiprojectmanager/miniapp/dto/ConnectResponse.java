package ru.hack.aiprojectmanager.miniapp.dto;

import java.util.List;

public record ConnectResponse(
        boolean connected,
        boolean requiresCompanySelection,
        List<CompanyDto> companies,
        List<BoardDto> boards
) {
    public static ConnectResponse selectCompany(List<CompanyDto> companies) {
        return new ConnectResponse(false, true, companies, List.of());
    }

    public static ConnectResponse success(List<BoardDto> boards) {
        return new ConnectResponse(true, false, List.of(), boards);
    }
}
