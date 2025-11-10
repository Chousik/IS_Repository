package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.chousik.is.entity.Person;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
}