package edu.rutmiit.enterprise.matchmaking.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ServiceTokenClientTest {
    @Test
    void jdkDoesNotResendRequestAfterLostResponse() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rating", exchange -> {
            count.incrementAndGet();
            exchange.close();
        });
        server.start();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/rating")).build();
            assertThatThrownBy(() -> client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()))
                    .isInstanceOf(java.io.IOException.class);
            assertThat(count).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test
    void customBoundedClientDecodesOAuthTokenAndCachesIt() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            count.incrementAndGet();
            byte[] body = "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var registration = ClientRegistration.withRegistrationId("rating")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri("http://127.0.0.1:" + server.getAddress().getPort() + "/token").build();
            var registrations = new InMemoryClientRegistrationRepository(registration);
            var manager = new HttpClientConfig().serviceAuthorizedClientManager(registrations,
                    new InMemoryOAuth2AuthorizedClientService(registrations));
            var request = OAuth2AuthorizeRequest.withClientRegistrationId("rating").principal("matchmaking-service").build();
            assertThat(manager.authorize(request).getAccessToken().getTokenValue()).isEqualTo("test-access-token");
            assertThat(manager.authorize(request).getAccessToken().getTokenValue()).isEqualTo("test-access-token");
            assertThat(count).hasValue(1);
        } finally { server.stop(0); }
    }
}
