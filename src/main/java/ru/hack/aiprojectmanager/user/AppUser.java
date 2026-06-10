package ru.hack.aiprojectmanager.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long telegramId;

    @Column(nullable = false)
    private Long chatId;

    private String yougileUserId;
    private String yougileApiKey;
    private String yougileRole;
    private String yougileCompanyId;
    private String yougileRealName;
    private String yougileEmail;
    private String username;
    private String fullName;
    private String timezone;

    @Column(nullable = false)
    @Builder.Default
    private Boolean digestEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean remindersEnabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
