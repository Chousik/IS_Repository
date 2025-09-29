package ru.chousik.is.dto;

import ru.chousik.is.entity.*;
import java.time.LocalDateTime;

public record StudyGroupDto(
        Long id,
        String name,
        Long coordinatesId,
        LocalDateTime creationDate,
        Long studentsCount,
        long expelledStudents,
        long transferredStudents,
        FormOfEducation formOfEducation,
        long shouldBeExpelled,
        Integer averageMark,
        Semester semesterEnum,
        Long groupAdminId
) {}
