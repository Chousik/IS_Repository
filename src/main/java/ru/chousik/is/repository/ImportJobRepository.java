package ru.chousik.is.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.chousik.is.entity.ImportJob;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findAllByEntityTypeOrderByCreatedAtDesc(String entityType);
}
