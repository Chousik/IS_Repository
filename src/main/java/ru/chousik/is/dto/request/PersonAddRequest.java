package ru.chousik.is.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.chousik.is.entity.Color;
import ru.chousik.is.entity.Country;

/**
 * DTO for creating {@link ru.chousik.is.entity.Person}
 */
public record PersonAddRequest(
        @NotBlank(message = "Поле name не может быть пустым") String name,
        Color eyeColor,
        @NotNull(message = "Поле hairColor не может быть пустым") Color hairColor,
        Long locationId,
        @Valid LocationAddRequest location,
        @NotNull(message = "Поле height не может быть пустым") @Positive(message = "Поле height должно быть больше 0") Long height,
        @NotNull(message = "Поле weight не может быть пустым") @Positive(message = "Поле weight должно быть больше 0") Float weight,
        Country nationality
) {
}
