package ru.chousik.is.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_group")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // >0, уникальное, генерируется автоматически

    @NotBlank
    @Column(nullable = false)
    private String name; // не null, не пустая строка

    @NotNull
    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "coordinates_id", nullable = false)
    private Coordinates coordinates; // отдельная сущность

    @NotNull
    @Column(nullable = false, updatable = false)
    private LocalDateTime creationDate; // генерируется автоматически

    @Positive
    private Long studentsCount; // >0, может быть null

    @Positive
    @Column(nullable = false)
    private long expelledStudents; // >0

    @Positive
    @Column(nullable = false)
    private long transferredStudents; // >0

    @Enumerated(EnumType.STRING)
    private FormOfEducation formOfEducation; // может быть null

    @Positive
    @Column(nullable = false)
    private long shouldBeExpelled; // >0

    @Positive
    private Integer averageMark; // >0, может быть null

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Semester semesterEnum; // не null

    @ManyToOne(cascade = CascadeType.ALL)
    private Person groupAdmin; // может быть null

    @PrePersist
    protected void onCreate() {
        this.creationDate = LocalDateTime.now();
    }
}
