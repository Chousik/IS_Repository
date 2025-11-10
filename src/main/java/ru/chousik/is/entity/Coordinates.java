package ru.chousik.is.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "coordinates",
        uniqueConstraints = @UniqueConstraint(name = "uq_coordinates_xy", columnNames = {"x", "y"}))
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
