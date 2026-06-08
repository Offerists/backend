package ru.hack.aiprojectmanager.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_context")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    @Id
    private Long telegramId;

    private String lastTaskId;
    private String lastTaskTitle;

    private String lastAssigneeId;
    private String lastAssigneeName;

    private String lastStatus;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
