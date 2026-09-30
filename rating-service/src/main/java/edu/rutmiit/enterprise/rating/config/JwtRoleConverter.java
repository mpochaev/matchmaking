package edu.rutmiit.enterprise.rating.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import java.util.*;

public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> result = new LinkedHashSet<>(scopes.convert(jwt));
        Object claim = jwt.getClaim("realm_access");
        if (claim instanceof Map<?, ?> realm && realm.get("roles") instanceof Collection<?> roles) {
            for (Object value : roles) {
                // Only explicitly supported realm roles become application roles.
                if (value instanceof String role && Set.of("service", "operator", "player").contains(role)) {
                    result.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
                }
            }
        }
        return result;
    }
}
