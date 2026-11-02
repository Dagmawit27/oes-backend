package com.oes.onlineexamsystem.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresInMs;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
