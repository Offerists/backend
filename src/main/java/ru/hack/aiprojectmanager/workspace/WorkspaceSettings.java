package ru.hack.aiprojectmanager.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "workspace_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSettings {

    @Id
    private Long chatId;

    @Column
    private String yougileApiKey;

    @Column
    private String columnTodoId;

    @Column
    private String columnInProgressId;

    @Column
    private String columnReviewId;

    @Column
    private String columnDoneId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isConfigured = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (isConfigured == null) isConfigured = false;
    }
}
