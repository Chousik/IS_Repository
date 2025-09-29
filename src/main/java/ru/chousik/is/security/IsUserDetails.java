package ru.chousik.is.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.chousik.is.entity.AuthoritiesEntity;
import ru.chousik.is.entity.UserEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class IsUserDetails implements UserDetails {
    private UserEntity user;
    private List<AuthoritiesEntity> authoritiesEntities;
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authoritiesEntities.stream()
                .map(AuthoritiesEntity::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
