package ru.hack.aiprojectmanager.kanban.yougile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileCompanyDto;
import ru.hack.aiprojectmanager.kanban.yougile.dto.YougileListResponse;

import java.util.List;
import java.util.Map;

@Component
public class YougileAuthClient {

    private final RestClient restClient;

    public YougileAuthClient(RestClient.Builder builder,
                             @Value("${yougile.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<YougileCompanyDto> getCompanies(String login, String password) {
        YougileListResponse<YougileCompanyDto> response = restClient.post()
                .uri("/auth/companies")
                .body(Map.of("login", login, "password", password))
                .retrieve()
                .body(new ParameterizedTypeReference<YougileListResponse<YougileCompanyDto>>() {});
        return response != null && response.content() != null ? response.content() : List.of();
    }

    public String createApiKey(String login, String password, String companyId) {
        ApiKeyResponse response = restClient.post()
                .uri("/auth/keys")
                .body(Map.of("login", login, "password", password, "companyId", companyId))
                .retrieve()
                .body(ApiKeyResponse.class);
        return response != null ? response.key() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiKeyResponse(String key) {}
}
