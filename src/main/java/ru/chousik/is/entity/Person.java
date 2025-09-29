package ru.chousik.is.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "person")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name; // не null, не пустая строка

    @Enumerated(EnumType.STRING)
    private Color eyeColor; // может быть null

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Color hairColor; // не null

    @ManyToOne(cascade = CascadeType.ALL)
    private Location location; // может быть null

    @NotNull
    @Positive
    @Column(nullable = false)
    private Long height; // >0, не null

    @Positive
    @Column(nullable = false)
    private float weight; // >0

    @Enumerated(EnumType.STRING)
    private Country nationality; // может быть null
}
