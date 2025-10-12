package ru.chousik.is.dto.response;

import jakarta.validation.constraints.NotNull;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.Semester;

import java.time.LocalDateTime;

/**
 * DTO for {@link ru.chousik.is.entity.StudyGroup}
 */
public record StudyGroupResponse(Long id, String name, @NotNull CoordinatesResponse coordinates,
                                 LocalDateTime creationDate, Long studentsCount, long expelledStudents,
                                 long transferredStudents, FormOfEducation formOfEducation, long shouldBeExpelled,
                                 Integer averageMark, Semester semesterEnum, PersonResponse groupAdmin) {
}