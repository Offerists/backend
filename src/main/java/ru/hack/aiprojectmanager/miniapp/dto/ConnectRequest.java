package ru.hack.aiprojectmanager.miniapp.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class ConnectRequest {
    private String email;
    private String password;
    private String companyId;
}
