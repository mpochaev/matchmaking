package edu.rutmiit.enterprise.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;

@Configuration
public class SecurityConfig {
    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientsInWebSession() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    @Bean
    ServerOAuth2AuthorizationRequestResolver authorizationRequests(ReactiveClientRegistrationRepository registrations) {
        var resolver = new DefaultServerOAuth2AuthorizationRequestResolver(registrations);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ServerOAuth2AuthorizationRequestResolver authorizationRequests,
            ServerOAuth2AuthorizedClientRepository authorizedClients) {
        return http
                .authorizeExchange(auth -> auth
                        .pathMatchers("/", "/index.html", "/favicon.ico", "/bff/csrf",
                                "/oauth2/**", "/login/**", "/actuator/health", "/actuator/health/**", "/actuator/prometheus", "/livez", "/readyz").permitAll()
                        .anyExchange().authenticated())
                .oauth2Login(login -> login.authorizationRequestResolver(authorizationRequests)
                        .authorizedClientRepository(authorizedClients))
                .oauth2Client(client -> client.authorizedClientRepository(authorizedClients))
                .logout(Customizer.withDefaults())
                .build();
    }
}
