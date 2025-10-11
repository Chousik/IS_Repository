//До лучших времен
//package ru.chousik.is.config;
//
//import com.nimbusds.jose.jwk.source.JWKSource;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.core.context.SecurityContext;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import ru.chousik.is.repository.AuthoritiesRepository;
//import ru.chousik.is.repository.UserRepository;
//import ru.chousik.is.security.IsUserDetailsService;
//
//@Configuration
//public class ToolsConfig {
//    @Bean
//    PasswordEncoder passwordEncoder(){
//        return new BCryptPasswordEncoder();
//    }
//    @Bean
//    public UserDetailsService userDetailsService(UserRepository userRepository,
//                                                 AuthoritiesRepository authoritiesRepository){
//        return new IsUserDetailsService(authoritiesRepository, userRepository);
//    }
//}
