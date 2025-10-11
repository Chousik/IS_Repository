package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chousik.is.entity.StudyGroup;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
}