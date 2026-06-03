package ru.hack.aiprojectmanager.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.List;
import java.util.Locale;

@Component
public class FindUserSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .required("name", "string", "Имя, фамилия или username пользователя (можно часть)")
            .build();

    private final AppUserRepository appUserRepository;

    public FindUserSkill(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public String getName() {
        return "find_user";
    }

    @Override
    public String getDescription() {
        return "Найти участника чата по имени/username, чтобы назначить на него задачу. "
                + "Возвращает telegram_id для передачи в create_task (поле assignee_telegram_id).";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long chatId, JsonNode args) {
        String query = requireText(args, "name").toLowerCase(Locale.ROOT);

        List<AppUser> matches = appUserRepository.findByChatId(chatId).stream()
                .filter(u -> matches(u, query))
                .toList();

        if (matches.isEmpty()) {
            return "Пользователь «" + query + "» не найден. "
                    + "Возможно, он ещё не подключился к боту в этом чате.";
        }

        StringBuilder sb = new StringBuilder();
        for (AppUser u : matches) {
            sb.append("• ").append(displayName(u));
            sb.append(" [telegram_id:").append(u.getTelegramId()).append("]");
            if (u.getYougileUserId() == null) {
                sb.append(" (не привязан к YouGile — назначить нельзя)");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private boolean matches(AppUser u, String query) {
        return contains(u.getFullName(), query) || contains(u.getUsername(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String displayName(AppUser u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) {
            return u.getUsername() != null ? u.getFullName() + " (@" + u.getUsername() + ")" : u.getFullName();
        }
        return u.getUsername() != null ? "@" + u.getUsername() : "id " + u.getTelegramId();
    }
}
