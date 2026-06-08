package ru.hack.aiprojectmanager.kanban;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_board_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBoardSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long telegramId;

    @Column(nullable = false)
    private String companyId;

    @Column(nullable = false)
    private String boardId;

    private String boardName;

    private String columnTodoId;
    private String columnInProgressId;
    private String columnReviewId;
    private String columnDoneId;

    @Column(nullable = false)
    private boolean isDefault;
}
