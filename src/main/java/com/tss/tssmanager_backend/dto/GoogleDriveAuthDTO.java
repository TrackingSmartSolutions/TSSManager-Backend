package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleDriveAuthDTO {
    private String authUrl;
    private String state;
    private Boolean isAuthenticated;
    private String email;
    private String message;
}