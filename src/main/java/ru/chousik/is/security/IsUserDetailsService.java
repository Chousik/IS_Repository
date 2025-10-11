//До лучших времен
//package ru.chousik.is.security;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import ru.chousik.is.entity.AuthoritiesEntity;
//import ru.chousik.is.entity.UserEntity;
//import ru.chousik.is.repository.AuthoritiesRepository;
//import ru.chousik.is.repository.UserRepository;
//
//import java.util.List;
//
//@RequiredArgsConstructor
//public class IsUserDetailsService implements UserDetailsService {
//    private final AuthoritiesRepository authoritiesRepository;
//    private final UserRepository userRepository;
//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        UserEntity user = userRepository.getUserEntitiesByUsername(username)
//                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
//        List<AuthoritiesEntity> authoritiesEntities = authoritiesRepository
//                .getAuthoritiesEntityByUser(user);
//        return new IsUserDetails(user, authoritiesEntities);
//    }
//}
