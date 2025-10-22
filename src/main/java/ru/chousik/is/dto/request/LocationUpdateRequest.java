package ru.chousik.is.dto.request;

/**
 * DTO for partial updates of {@link ru.chousik.is.entity.Location}
 */
public record LocationUpdateRequest(
        Integer x,
        Double y,
        Double z,
        String name
) {
}
