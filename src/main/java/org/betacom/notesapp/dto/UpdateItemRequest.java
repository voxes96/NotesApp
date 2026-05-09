package org.betacom.notesapp.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateItemRequest (
    String title,
    String content,
    @NotNull(message = "Version is required")
    Integer version
) {}
