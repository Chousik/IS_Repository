package ru.chousik.is.entity;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "coordinates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coordinates_xy",
                columnNames = {"x", "y"}))
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "coordinates")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Coordinates {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long x;

    @NotNull
    @Column(nullable = false)
    private Float y; // не null
}
