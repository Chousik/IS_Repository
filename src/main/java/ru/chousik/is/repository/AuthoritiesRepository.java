//До лучших времен
//package ru.chousik.is.repository;
//
//import jakarta.transaction.Transactional;
//import jakarta.validation.constraints.NotNull;
//import jakarta.validation.constraints.Size;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//import ru.chousik.is.entity.AuthoritiesEntity;
//import ru.chousik.is.entity.UserEntity;
//
//import java.util.List;
//
//@Repository
//public interface AuthoritiesRepository extends JpaRepository<AuthoritiesEntity, String> {
//    List<AuthoritiesEntity> getAuthoritiesEntityByUser(UserEntity user);
//    @Transactional
//    void removeByAuthorityAndUser(@Size(max = 50) @NotNull String authority, UserEntity user);
//    @Transactional
//    void removeByUser(UserEntity user);
//}
