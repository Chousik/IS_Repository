package ru.chousik.is.dto.response;

import ru.chousik.is.entity.Color;
import ru.chousik.is.entity.Country;

/**
 * DTO for {@link ru.chousik.is.entity.Person}
 */
public record PersonResponse(Long id, String name, Color eyeColor, Color hairColor, LocationResponse location, Long height, float weight, Country nationality) {
}