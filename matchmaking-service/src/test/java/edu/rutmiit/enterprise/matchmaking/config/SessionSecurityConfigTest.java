package edu.rutmiit.enterprise.matchmaking.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSecurityConfigTest {
    private final SessionSecurityConfig configuration = new SessionSecurityConfig();

    @Test
    void passwordsAreHashedAndOperatorGetsBothRoles() {
        var encoder = configuration.passwordEncoder();
        var users = configuration.users(encoder);
        var operator = users.loadUserByUsername("operator");

        assertThat(operator.getPassword()).isNotEqualTo("operator");
        assertThat(encoder.matches("operator", operator.getPassword())).isTrue();
        assertThat(AuthorityUtils.authorityListToSet(operator.getAuthorities()))
                .containsExactlyInAnyOrder("ROLE_PLAYER", "ROLE_OPERATOR");
    }
}
