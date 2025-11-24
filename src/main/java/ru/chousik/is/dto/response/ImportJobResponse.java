package ru.chousik.is.dto.response;

import ru.chousik.is.entity.ImportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ImportJobResponse(
        UUID id,
        String entityType,
        ImportStatus status,
        String filename,
        Integer totalRecords,
        Integer successCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
