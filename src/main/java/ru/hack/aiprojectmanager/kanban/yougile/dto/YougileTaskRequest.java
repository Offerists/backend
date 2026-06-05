package ru.hack.aiprojectmanager.kanban.yougile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YougileTaskRequest {
    private String title;
    private String columnId;
    private Deadline deadline;       // YouGile требует объект {deadline, withTime}
    private List<String> assigned;  // YouGile API требует UUID array
    private String description;

    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Deadline {
        private Long deadline;
        private Long startDate;
        private Boolean withTime;
    }

    public static Deadline deadlineOf(Long deadlineMillis, Long startDateMillis) {
        if (deadlineMillis == null && startDateMillis == null) return null;
        return new Deadline(deadlineMillis, startDateMillis, false);
    }
}
