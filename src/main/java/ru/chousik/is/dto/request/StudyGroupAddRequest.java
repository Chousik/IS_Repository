package ru.chousik.is.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.Semester;

/**
 * DTO for creating {@link ru.chousik.is.entity.StudyGroup}
 */
public record StudyGroupAddRequest(
        @NotBlank(message = "Поле name не может быть пустым") String name,
        Long coordinatesId,
        @Valid CoordinatesAddRequest coordinates,
        @Positive(message = "Поле studentsCount должно быть больше 0") Long studentsCount,
        @Positive(message = "Поле expelledStudents должно быть больше 0") long expelledStudents,
        @Positive(message = "Поле transferredStudents должно быть больше 0") long transferredStudents,
        FormOfEducation formOfEducation,
        @Positive(message = "Поле shouldBeExpelled должно быть больше 0") long shouldBeExpelled,
        @Positive(message = "Поле averageMark должно быть больше 0") Integer averageMark,
        @NotNull(message = "Поле semesterEnum не может быть пустым") Semester semesterEnum,
        Long groupAdminId,
        @Valid PersonAddRequest groupAdmin
) {
}
