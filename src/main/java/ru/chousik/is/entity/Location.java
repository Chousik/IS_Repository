package ru.chousik.is.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "location")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int x;

    @NotNull
    @Column(nullable = false)
    private Double y; // не null

    private double z;

    @NotBlank
    @Column(nullable = false)
    private String name; // не null, не пустая строка
}
