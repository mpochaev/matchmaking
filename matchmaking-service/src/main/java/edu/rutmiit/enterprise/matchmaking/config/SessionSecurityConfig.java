package edu.rutmiit.enterprise.matchmaking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Profile("session-auth")
@Configuration
@EnableMethodSecurity
public class SessionSecurityConfig {
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain sessionSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/api/public/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .formLogin(form -> form.defaultSuccessUrl("/api/lobbies", true))
                .logout(logout -> logout.deleteCookies("JSESSIONID", "XSRF-TOKEN"))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("player")
                        .password(encoder.encode("player"))
                        .roles("PLAYER")
                        .build(),
                User.withUsername("operator")
                        .password(encoder.encode("operator"))
                        .roles("PLAYER", "OPERATOR")
                        .build()
        );
    }
}
