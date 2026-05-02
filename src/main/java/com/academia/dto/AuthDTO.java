package com.academia.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDTO {

    public record LoginRequest(
            @NotBlank(message = "Usuário é obrigatório")
            String username,

            @NotBlank(message = "Senha é obrigatória")
            String password
    ) {}

    public record LoginResponse(
            String token,
            String username
    ) {}
}
