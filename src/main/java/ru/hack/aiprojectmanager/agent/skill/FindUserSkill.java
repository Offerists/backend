package ru.hack.aiprojectmanager.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.hack.aiprojectmanager.user.AppUser;
import ru.hack.aiprojectmanager.user.AppUserRepository;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FindUserSkill {

    private final AppUserRepository appUserRepository;

    @Tool(name = "find_user", description = "Найти участника. name='all' — все участники, name=имя/@username — конкретный.")
    public String findUser(
            @ToolParam(description = "Имя, @username, часть имени или 'all' для всех участников") String name,
            org.springframework.ai.chat.model.ToolContext ctx) {

        Long telegramUserId = (Long) ctx.getContext().get("telegramUserId");
        AppUser requester = appUserRepository.findFirstByTelegramId(telegramUserId).orElse(null);

        List<AppUser> users = requester != null && requester.getYougileCompanyId() != null
                ? appUserRepository.findByYougileCompanyId(requester.getYougileCompanyId())
                : appUserRepository.findByChatId(telegramUserId);

        if (users.isEmpty()) return "Зарегистрированных участников нет.";

        boolean listAll = name == null || name.isBlank() || "all".equalsIgnoreCase(name)
                || "все".equalsIgnoreCase(name);

        List<AppUser> matches = listAll ? users
                : users.stream().filter(u -> matches(u, name.toLowerCase(Locale.ROOT))).toList();

        if (matches.isEmpty()) return "Пользователь «" + name + "» не найден.";

        var sb = new StringBuilder();
        for (AppUser u : matches) {
            sb.append("• ").append(displayName(u));
            if (u.getYougileUserId() == null) sb.append(" (не привязан к YouGile)");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private boolean matches(AppUser u, String q) {
        return (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q));
    }

    private String displayName(AppUser u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) {
            return u.getUsername() != null ? u.getFullName() + " (@" + u.getUsername() + ")" : u.getFullName();
        }
        return u.getUsername() != null ? "@" + u.getUsername() : "пользователь";
    }
}
