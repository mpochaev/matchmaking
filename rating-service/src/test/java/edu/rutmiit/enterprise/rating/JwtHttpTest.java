package edu.rutmiit.enterprise.rating;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtHttpTest {
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static final String ISSUER;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
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
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ISSUER + "/jwks");
    }

    @LocalServerPort int port;
    private static final String RATING = "/internal/ratings/22222222-2222-4222-8222-222222222222";

    @AfterAll static void stopKeys() { JWKS.stop(0); }

    @Test void validTokenAndRoleMatrix() throws Exception {
        String service = token(KEY, ISSUER, "rating-service", "service", 300, -5);
        String operator = token(KEY, ISSUER, "rating-service", "operator", 300, -5);
        assertThat(call(RATING, service)).isEqualTo(200);
        assertThat(call("/internal/admin/info", service)).isEqualTo(403);
        assertThat(call(RATING, operator)).isEqualTo(403);
        assertThat(call("/internal/admin/info", operator)).isEqualTo(200);
    }

    @Test void missingOrInvalidCredentialsAreRejected() throws Exception {
        assertThat(call(RATING, null)).isEqualTo(401);
        assertThat(call(RATING, "not-a-jwt")).isEqualTo(401);
        assertThat(call(RATING, token(KEY, ISSUER, "other-api", "service", 300, -5))).isEqualTo(401);
        assertThat(call(RATING, token(KEY, ISSUER + "/other", "rating-service", "service", 300, -5))).isEqualTo(401);
        assertThat(call(RATING, token(KEY, ISSUER, "rating-service", "service", -120, -300))).isEqualTo(401);
        assertThat(call(RATING, token(KEY, ISSUER, "rating-service", "service", 600, 300))).isEqualTo(401);
        RSAKey impostor = new RSAKeyGenerator(2048).keyID("test-key").generate();
        assertThat(call(RATING, token(impostor, ISSUER, "rating-service", "service", 300, -5))).isEqualTo(401);
    }

    @Test void unsupportedRolesDoNotGrantAccess() throws Exception {
        assertThat(call(RATING, token(KEY, ISSUER, "rating-service", "admin", 300, -5))).isEqualTo(403);
    }

    @Test void userApiAcceptsOnlyUserRoles() throws Exception {
        String player = token(KEY, ISSUER, "rating-service", "player", 300, -5);
        String operator = token(KEY, ISSUER, "rating-service", "operator", 300, -5);
        String service = token(KEY, ISSUER, "rating-service", "service", 300, -5);
        assertThat(call("/api/ranks", null)).isEqualTo(401);
        assertThat(call("/api/ranks", player)).isEqualTo(200);
        assertThat(call("/api/ranks", operator)).isEqualTo(200);
        assertThat(call("/api/ranks", service)).isEqualTo(403);
        assertThat(call(RATING, player)).isEqualTo(403);
        assertThat(call("/internal/admin/info", player)).isEqualTo(403);
    }

    private int call(String path, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (token != null) request.header("Authorization", "Bearer " + token);
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.headers().allValues("set-cookie")).isEmpty();
            return response.statusCode();
        }
    }

    private String token(RSAKey key, String issuer, String audience, String role, long expires, long notBefore) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("test-service")
                .audience(audience).issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.plusSeconds(expires)))
                .notBeforeTime(Date.from(now.plusSeconds(notBefore)))
                .claim("realm_access", Map.of("roles", List.of(role))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
