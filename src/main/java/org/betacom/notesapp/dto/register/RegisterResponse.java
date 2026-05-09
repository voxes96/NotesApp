package org.betacom.notesapp.dto.register;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegisterResponse (
    
    UUID id,
    String login,
    LocalDateTime createdAt
) {}

