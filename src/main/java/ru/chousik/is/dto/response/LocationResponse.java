package ru.chousik.is.dto.response;

/**
 * DTO for {@link ru.chousik.is.entity.Location}
 */
public record LocationResponse(Long id, int x, Double y, double z, String name) {
}