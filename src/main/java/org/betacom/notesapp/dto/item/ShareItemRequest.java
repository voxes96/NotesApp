package org.betacom.notesapp.dto.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShareItemRequest (
    @NotNull(message = "User ID is required")
    UUID userId,
    @NotBlank(message = "Role is required")
    String role
) {}
