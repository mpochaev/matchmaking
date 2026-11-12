package edu.rutmiit.enterprise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void confidentialClientAlsoUsesPkceS256() {
        var registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId("matchmaking-bff").clientSecret("test-only-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://issuer.test/authorize")
                .tokenUri("https://issuer.test/token").build();
        var resolver = new SecurityConfig().authorizationRequests(
                new InMemoryReactiveClientRegistrationRepository(registration));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                "http://localhost:18088/oauth2/authorization/keycloak"));
        var request = resolver.resolve(exchange).block();
        assertThat(request).isNotNull();
        assertThat(request.getAdditionalParameters()).containsEntry("code_challenge_method", "S256");
        assertThat(request.getAdditionalParameters().get("code_challenge")).isInstanceOf(String.class);
        assertThat(request.getAttributes()).containsKey("code_verifier");
    }

    @Test
    void authorizedClientsAreBoundToWebSession() {
        assertThat(new SecurityConfig().authorizedClientsInWebSession())
                .isInstanceOf(WebSessionServerOAuth2AuthorizedClientRepository.class);
    }
}
