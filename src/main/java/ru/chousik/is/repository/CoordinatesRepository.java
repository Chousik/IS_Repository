package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chousik.is.entity.Coordinates;

public interface CoordinatesRepository extends JpaRepository<Coordinates, Long> {
}