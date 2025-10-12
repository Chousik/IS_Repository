package ru.chousik.is.dto.request;

/**
 * DTO for partial updates of {@link ru.chousik.is.entity.Coordinates}
 */
public record CoordinatesUpdateRequest(Long x, Float y) {
}
