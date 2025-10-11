package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chousik.is.entity.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {
}