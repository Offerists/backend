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
@Table(name = "group_context")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupContext {

    @Id
    private Long chatId;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    @Builder.Default
    private int totalMessages = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
