package ru.chousik.is.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String name; // генерируется автоматически, уникальное

    @NotNull
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, optional = false)
    @JoinColumn(name = "coordinates_id", nullable = false)
    private Coordinates coordinates; // отдельная сущность

    @NotNull
    @Column(nullable = false, updatable = false)
    private LocalDateTime creationDate; // генерируется автоматически

    @NotNull
    @Positive
    @Column(nullable = false)
    private Long studentsCount; // >0

    @Positive
    @Column(nullable = false)
    private long expelledStudents; // >0

    @Positive
    @Column(nullable = false)
    private int course; // курс обучения

    @Column(nullable = false)
    private int sequenceNumber; // номер внутри курса+формы для генерации имени

    @Positive
    @Column(nullable = false)
    private long transferredStudents; // >0

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormOfEducation formOfEducation; // обязателен для бизнес-ограничений

    @Positive
    @Column(nullable = false)
    private long shouldBeExpelled; // >0

    @Positive
    private Integer averageMark; // >0, может быть null

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Semester semesterEnum; // не null

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "group_admin_id", unique = true)
    private Person groupAdmin; // может быть null, но не повторяется в других группах

    @PrePersist
    protected void onCreate() {
        this.creationDate = LocalDateTime.now();
    }
}
