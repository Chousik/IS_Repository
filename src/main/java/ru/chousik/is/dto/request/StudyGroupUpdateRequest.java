package ru.chousik.is.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.Semester;

/**
 * DTO for partial updates of {@link ru.chousik.is.entity.StudyGroup}
 */
public record StudyGroupUpdateRequest(
        String name,
        Long coordinatesId,
        @Valid CoordinatesAddRequest coordinates,
        @Positive(message = "Поле studentsCount должно быть больше 0") Long studentsCount,
        Boolean clearStudentsCount,
        @Positive(message = "Поле expelledStudents должно быть больше 0") Long expelledStudents,
        @Positive(message = "Поле transferredStudents должно быть больше 0") Long transferredStudents,
        FormOfEducation formOfEducation,
        Boolean clearFormOfEducation,
        @Positive(message = "Поле shouldBeExpelled должно быть больше 0") Long shouldBeExpelled,
        @Positive(message = "Поле averageMark должно быть больше 0") Integer averageMark,
        Boolean clearAverageMark,
        Semester semesterEnum,
        Long groupAdminId,
        @Valid PersonAddRequest groupAdmin,
        Boolean removeGroupAdmin
) {
}
