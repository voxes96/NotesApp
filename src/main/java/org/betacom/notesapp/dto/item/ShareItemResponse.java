package org.betacom.notesapp.dto.item;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShareItemResponse (
    UUID itemId,
    UUID userId,
    String role,
    LocalDateTime grantedAt
) {}
