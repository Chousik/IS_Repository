package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.chousik.is.entity.StudyGroup;
import ru.chousik.is.entity.Semester;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    List<StudyGroup> findAllBySemesterEnum(Semester semesterEnum);

    Optional<StudyGroup> findFirstBySemesterEnum(Semester semesterEnum);

    @Query("select sg.shouldBeExpelled as shouldBeExpelled, count(sg) as total from StudyGroup sg group by sg.shouldBeExpelled")
    List<ShouldBeExpelledGroupProjection> countGroupedByShouldBeExpelled();

    @Query("select coalesce(sum(sg.expelledStudents), 0) from StudyGroup sg")
    Long sumExpelledStudents();

    boolean existsByCoordinatesId(Long coordinatesId);

    interface ShouldBeExpelledGroupProjection {
        long getShouldBeExpelled();

        long getTotal();
    }
}
