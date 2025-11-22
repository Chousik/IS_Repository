package ru.chousik.is.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.entity.StudyGroup;

@Repository
public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    List<StudyGroup> findAllBySemesterEnum(Semester semesterEnum);

    Optional<StudyGroup> findFirstBySemesterEnum(Semester semesterEnum);

    @Query("select sg.shouldBeExpelled as shouldBeExpelled, "
            + "count(sg) as total from StudyGroup sg group by sg.shouldBeExpelled")
    List<ShouldBeExpelledGroupProjection> countGroupedByShouldBeExpelled();

    @Query("select coalesce(sum(sg.expelledStudents), 0) from StudyGroup sg")
    Long sumExpelledStudents();

    boolean existsByCoordinatesId(Long coordinatesId);

    @Query("select coalesce(max(sg.sequenceNumber), 0) from StudyGroup sg "
            + "where sg.formOfEducation = :form and sg.course = :course")
    int findMaxSequenceNumber(@Param("form") FormOfEducation form, @Param("course") int course);

    boolean existsByGroupAdminId(Long groupAdminId);

    boolean existsByGroupAdminIdAndIdNot(Long groupAdminId, Long id);

    List<StudyGroup> findByCourseAndFormOfEducationIn(int course, List<FormOfEducation> forms);

    interface ShouldBeExpelledGroupProjection {
        long getShouldBeExpelled();

        long getTotal();
    }
}
