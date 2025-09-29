package ru.chousik.is.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "authorities")
public class AuthoritiesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "username")
    private UserEntity user;

    @Size(max = 50)
    @NotNull
    String authority;

    public AuthoritiesEntity(UserEntity user, String authority) {
        this.user = user;
        this.authority = authority;
    }

    @Override
    public String toString() {
        return authority;
    }
}
