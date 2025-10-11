package ru.chousik.is.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.chousik.is.entity.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {
}