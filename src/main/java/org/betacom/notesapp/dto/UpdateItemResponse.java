package org.betacom.notesapp.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateItemResponse (
    UUID id,
    String title,
    String content,
    Integer version,
    LocalDateTime updatedAt
) {}
