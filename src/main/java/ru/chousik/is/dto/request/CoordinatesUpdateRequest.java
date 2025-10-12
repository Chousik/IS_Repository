package ru.chousik.is.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for {@link ru.chousik.is.entity.Coordinates}
 */
public record CoordinatesUpdateRequest(Long id, long x, @NotNull(message = "Поле y не может быть null") Float y) {
}