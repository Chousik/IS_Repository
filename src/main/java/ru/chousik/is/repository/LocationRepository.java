package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.chousik.is.entity.Location;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
}
