package org.betacom.notesapp.dto.item;

import java.time.LocalDateTime;
import java.util.UUID;

public record ItemListResponse (
        UUID id,
        String title,
        String content,
        Integer version,
        UUID ownerId,
        LocalDateTime updatedAt
) {}