package ru.chousik.is.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for creating {@link ru.chousik.is.entity.Location}
 */
public record LocationAddRequest(
        int x,
        @NotNull(message = "Поле y не может быть null") Double y,
        double z,
        @NotBlank(message = "Поле name не может быть пустым") String name
) {
}
