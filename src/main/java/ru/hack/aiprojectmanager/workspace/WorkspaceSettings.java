package ru.hack.aiprojectmanager.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workspace_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSettings {

    @Id
    private Long chatId;

    @Column(nullable = false)
    private String yougileApiKey;

    @Column(nullable = false)
    private String columnTodoId;

    @Column(nullable = false)
    private String columnInProgressId;

    @Column
    private String columnReviewId;

    @Column(nullable = false)
    private String columnDoneId;
}
