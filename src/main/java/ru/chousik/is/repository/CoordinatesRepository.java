package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import ru.chousik.is.entity.Coordinates;

@Repository
public interface CoordinatesRepository extends JpaRepository<Coordinates, Long>, JpaSpecificationExecutor<Coordinates> {
}