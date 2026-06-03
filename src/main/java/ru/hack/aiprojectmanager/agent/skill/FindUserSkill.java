package ru.hack.aiprojectmanager.agent.skill;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.storage.AppUser;
import ru.hack.aiprojectmanager.storage.AppUserRepository;

import java.util.List;
import java.util.Locale;

@Component
public class FindUserSkill implements Skill {

    private static final JsonNode SCHEMA = SchemaBuilder.object()
            .optional("name", "string", "Имя, фамилия или username (часть). Если не указано — вернёт всех участников.")
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
        return "Найти участника по имени/username для назначения на задачу. "
                + "Без параметра name — возвращает всех участников компании. "
                + "Возвращает telegram_id для передачи в create_task (assignee_telegram_id).";
    }

    @Override
    public JsonNode getParametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Long telegramUserId, JsonNode args) {
        String query = optText(args, "name");

        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);
        List<AppUser> users = requester != null && requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                : appUserRepository.findByChatId(telegramUserId); // fallback для личного чата

        if (users.isEmpty()) {
            return "Зарегистрированных участников пока нет.";
        }

        List<AppUser> matches = query == null
                ? users
                : users.stream().filter(u -> matches(u, query.toLowerCase(Locale.ROOT))).toList();

        if (matches.isEmpty()) {
            return "Пользователь «" + query + "» не найден. "
                    + "Вызови find_user без имени, чтобы увидеть всех участников.";
        }

        StringBuilder sb = new StringBuilder();
        for (AppUser u : matches) {
            sb.append("• ").append(displayName(u));
            if (u.getYougileUserId() == null) {
                sb.append(" (не привязан к YouGile)");
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
        return u.getUsername() != null ? "@" + u.getUsername() : "пользователь " + u.getTelegramId();
    }
}
