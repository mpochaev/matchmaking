package edu.rutmiit.enterprise.rating.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {
    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void keepsScopesAndOnlyExplicitlySupportedRoles() {
        var jwt = Jwt.withTokenValue("already-verified-test-fixture").header("alg", "RS256")
                .subject("service").claim("scope", "players.read players.write")
                .claim("realm_access", Map.of("roles", List.of("service", "operator", "admin", "SERVICE", 7)))
                .build();
        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_players.read", "SCOPE_players.write", "ROLE_SERVICE", "ROLE_OPERATOR");
    }

    @Test
    void unexpectedRoleStructureDoesNotCreatePrivileges() {
        for (Object claim : List.of("service", Map.of("roles", "service"), Map.of("roles", List.of(7)))) {
            var jwt = Jwt.withTokenValue("already-verified-test-fixture").header("alg", "RS256")
                    .subject("service").claim("realm_access", claim).build();
            assertThat(converter.convert(jwt)).isEmpty();
        }
    }
}
