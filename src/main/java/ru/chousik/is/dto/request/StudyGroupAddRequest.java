package ru.chousik.is.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.Semester;

/**
 * DTO for creating {@link ru.chousik.is.entity.StudyGroup}
 */
public record StudyGroupAddRequest(
        Long coordinatesId,
        @Valid CoordinatesAddRequest coordinates,
        @NotNull(message = "Поле studentsCount не может быть пустым")
        @Positive(message = "Поле studentsCount должно быть больше 0") Long studentsCount,
        @Positive(message = "Поле expelledStudents должно быть больше 0") long expelledStudents,
        @NotNull(message = "Поле course не может быть пустым")
        @Positive(message = "Поле course должно быть больше 0") Integer course,
        @Positive(message = "Поле transferredStudents должно быть больше 0") long transferredStudents,
        @NotNull(message = "Поле formOfEducation не может быть пустым") FormOfEducation formOfEducation,
        @Positive(message = "Поле shouldBeExpelled должно быть больше 0") long shouldBeExpelled,
        @Positive(message = "Поле averageMark должно быть больше 0") Integer averageMark,
        @NotNull(message = "Поле semesterEnum не может быть пустым") Semester semesterEnum,
        Long groupAdminId,
        @Valid PersonAddRequest groupAdmin
) {
}
