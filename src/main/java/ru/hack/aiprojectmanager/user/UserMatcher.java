package ru.hack.aiprojectmanager.user;

import java.util.List;
import java.util.Locale;

/**
 * Сопоставление участника по любому из известных идентификаторов:
 * telegram username, telegram имя, YouGile ФИО, YouGile email.
 */
public final class UserMatcher {

    private static final List<String> SELF_REFERENCES = List.of("себя", "я", "меня", "me");

    private UserMatcher() {}

    /** Ссылается ли строка на самого пользователя ("себя", "я", "me" и т.п.). */
    public static boolean isSelfReference(String s) {
        return s != null && SELF_REFERENCES.contains(s.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean matches(AppUser u, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return false;
        return contains(u.getUsername(), q)
                || contains(u.getFullName(), q)
                || contains(u.getYougileRealName(), q)
                || contains(u.getYougileEmail(), q)
                || contains(emailLocalPart(u.getYougileEmail()), q);
    }

    /** Имя для отображения пользователю: предпочитаем реальное имя из YouGile. */
    public static String displayName(AppUser u) {
        String name = !isBlank(u.getYougileRealName()) ? u.getYougileRealName() : u.getFullName();
        if (isBlank(name)) {
            return u.getUsername() != null ? "@" + u.getUsername() : "пользователь";
        }
        return u.getUsername() != null ? name + " (@" + u.getUsername() + ")" : name;
    }

    private static boolean contains(String field, String query) {
        return field != null && normalize(field).contains(query);
    }

    private static String emailLocalPart(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT).trim();
        return s.startsWith("@") ? s.substring(1) : s;
    }
}
