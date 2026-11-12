package edu.rutmiit.enterprise.matchmaking;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Testcontainers(disabledWithoutDocker = true)
class MatchmakingJwtHttpTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static final String ISSUER;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("matchmaking-test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ISSUER = "http://127.0.0.1:" + JWKS.getAddress().getPort();
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            JWKS.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ISSUER + "/jwks");
    }

    @LocalServerPort int port;
    @AfterAll static void stopKeys() { JWKS.stop(0); }

    @Test
    void playerReadsAndOnlyOperatorCreates() throws Exception {
        assertThat(call("GET", token("matchmaking-service", "player"))).isEqualTo(200);
        assertThat(call("POST", token("matchmaking-service", "player"))).isEqualTo(403);
        assertThat(call("POST", token("matchmaking-service", "operator"))).isEqualTo(201);
    }

    @Test
    void idempotencyKeyDoesNotReplaceOperatorRole() throws Exception {
        assertThat(call("POST", token("matchmaking-service", "player"), "/api/players/idempotent")).isEqualTo(403);
        assertThat(call("POST", token("matchmaking-service", "operator"), "/api/players/idempotent")).isEqualTo(201);
    }

    @Test
    void signatureAloneDoesNotGrantAccessToThisApi() throws Exception {
        assertThat(call("GET", null)).isEqualTo(401);
        assertThat(call("GET", token("rating-service", "operator"))).isEqualTo(401);
        assertThat(call("GET", token("matchmaking-service", "service"))).isEqualTo(403);
    }

    @Test
    void cookieIsNotAnAuthenticationMechanismInJwtProfile() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/lobbies"))
                    .header("Cookie", "JSESSIONID=untrusted; SESSION=untrusted").build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().allValues("set-cookie")).isEmpty();
        }
    }

    private int call(String method, String accessToken) throws Exception {
        return call(method, accessToken, "/api/players");
    }

    private int call(String method, String accessToken, String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (path.endsWith("/idempotent")) request.header("Idempotency-Key", java.util.UUID.randomUUID().toString());
            if (accessToken != null) request.header("Authorization", "Bearer " + accessToken);
            if (method.equals("POST")) request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"JWT Player " + java.util.UUID.randomUUID() + "\"}"));
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.headers().allValues("set-cookie")).isEmpty();
            return response.statusCode();
        }
    }

    private String token(String audience, String role) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject("matchmaking-test-user")
                .audience(audience).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("realm_access", Map.of("roles", List.of(role))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }
}
