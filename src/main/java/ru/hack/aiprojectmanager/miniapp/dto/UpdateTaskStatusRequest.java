package ru.hack.aiprojectmanager.miniapp.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateTaskStatusRequest {
    private String status;
}
