package ru.chousik.is.dto.request;

import jakarta.validation.Valid;
import ru.chousik.is.entity.Color;
import ru.chousik.is.entity.Country;

/**
 * DTO for partial updates of {@link ru.chousik.is.entity.Person}
 */
public record PersonUpdateRequest(
        String name,
        Color eyeColor,
        Color hairColor,
        Long locationId,
        @Valid LocationAddRequest location,
        Boolean removeLocation,
        Long height,
        Float weight,
        Country nationality
) {
}
