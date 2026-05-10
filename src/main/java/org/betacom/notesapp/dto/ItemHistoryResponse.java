package org.betacom.notesapp.dto;

import java.time.LocalDateTime;

public record ItemHistoryResponse (
    Integer revision,
    String revisionType,
    LocalDateTime timestamp,
    String changedBy,
    String title,
    String content
) {}
