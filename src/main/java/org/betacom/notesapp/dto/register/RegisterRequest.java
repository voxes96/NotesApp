package org.betacom.notesapp.dto.register;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
    @NotBlank(message = "Login is required")
    @Size(min = 3, max = 64, message = "Login must be between 3 and 64 characters")
    String login,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}