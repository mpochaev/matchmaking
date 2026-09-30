package edu.rutmiit.enterprise.matchmaking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("session-auth")
@Testcontainers(disabledWithoutDocker = true)
class SessionHttpTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Test
    void lessonRequestsEnforceAuthenticationCsrfRolesAndLogout() throws Exception {
        Browser operator = new Browser();
        var anonymous = operator.get("/api/lobbies");
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(anonymous.headers().firstValue("location").orElseThrow()).endsWith("/login");
        operator.login("operator");
        assertThat(operator.get("/api/lobbies").statusCode()).isEqualTo(200);
        String operatorToken = operator.token();
        assertThat(operator.post("/api/players", "{\"nickname\":\"HTTP Test Player\"}",
                "application/json", operatorToken).statusCode()).isEqualTo(201);
        assertThat(operator.post("/api/players", "{\"nickname\":\"Missing CSRF\"}",
                "application/json", null).statusCode()).isEqualTo(403);
        String rawToken = operator.cookie("XSRF-TOKEN");
        assertThat(rawToken).isNotEqualTo(operatorToken);
        assertThat(operator.post("/api/players", "{\"nickname\":\"Raw CSRF\"}",
                "application/json", rawToken).statusCode()).isEqualTo(403);

        Browser player = new Browser();
        player.login("player");
        assertThat(player.get("/api/lobbies").statusCode()).isEqualTo(200);
        assertThat(player.post("/api/players", "{\"nickname\":\"Forbidden Player\"}",
                "application/json", player.token()).statusCode()).isEqualTo(403);

        String oldId = operator.cookie("JSESSIONID");
        assertThat(operator.post("/logout", "", "application/x-www-form-urlencoded",
                operator.token()).statusCode()).isEqualTo(302);
        assertThat(operator.get("/api/lobbies").statusCode()).isEqualTo(302);
        try (HttpClient replay = HttpClient.newHttpClient()) {
            var response = replay.send(HttpRequest.newBuilder(uri("/api/lobbies"))
                    .header("Cookie", "JSESSIONID=" + oldId).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("location").orElseThrow()).endsWith("/login");
        }
    }

    @Test
    void newLoginExpiresPreviousSessionAndLogoutAllowsAnotherLogin() throws Exception {
        Browser first = new Browser();
        first.login("operator");
        Browser second = new Browser();
        second.login("operator");
        var expired = first.get("/api/lobbies");
        // The default expiration strategy returns a message, not protected JSON.
        assertThat(expired.body()).contains("session has been expired");
        assertThat(second.get("/api/lobbies").statusCode()).isEqualTo(200);
        assertThat(second.post("/logout", "", "application/x-www-form-urlencoded",
                second.token()).statusCode()).isEqualTo(302);
        Browser third = new Browser();
        third.login("operator");
        assertThat(third.get("/api/lobbies").statusCode()).isEqualTo(200);
        third.post("/logout", "", "application/x-www-form-urlencoded", third.token());
    }

    @Test
    void operatorCabinetSeparatesAnonymousPlayerAndOperator() throws Exception {
        Browser anonymous = new Browser();
        assertThat(anonymous.get("/api/public/status").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/api/diagnostics").statusCode()).isEqualTo(302);

        Browser operator = new Browser();
        operator.login("operator");
        var host = operator.post("/api/players", "{\"nickname\":\"Cabinet Host\"}",
                "application/json", operator.token());
        assertThat(host.statusCode()).isEqualTo(201);
        var hostId = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(host.body());
        assertThat(hostId.find()).isTrue();

        Browser player = new Browser();
        player.login("player");
        assertThat(player.post("/api/lobbies",
                "{\"name\":\"Player Lobby\",\"code\":\"CAB001\",\"hostId\":\"" + hostId.group(1) + "\"}",
                "application/json", player.token()).statusCode()).isEqualTo(201);
        assertThat(player.get("/api/diagnostics").statusCode()).isEqualTo(403);

        var diagnostics = operator.get("/api/diagnostics");
        assertThat(diagnostics.statusCode()).isEqualTo(200);
        assertThat(diagnostics.body()).contains("\"players\"", "\"lobbies\"");

        player.post("/logout", "", "application/x-www-form-urlencoded", player.token());
        operator.post("/logout", "", "application/x-www-form-urlencoded", operator.token());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body, String type, String token) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", type);
            if (token != null) request.header("X-XSRF-TOKEN", token);
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        void login(String user) throws Exception {
            var page = get("/login");
            assertThat(page.statusCode()).isEqualTo(200);
            var match = Pattern.compile("name=\"_csrf\" type=\"hidden\" value=\"([^\"]+)\"")
                    .matcher(page.body());
            assertThat(match.find()).isTrue();
            var response = post("/login", "username=" + user + "&password=" + user + "&_csrf="
                    + URLEncoder.encode(match.group(1), StandardCharsets.UTF_8),
                    "application/x-www-form-urlencoded", null);
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("location").orElseThrow()).endsWith("/api/lobbies");
            assertThat(response.headers().allValues("set-cookie").toString())
                    .contains("JSESSIONID=", "HttpOnly", "SameSite=Lax");
        }

        String token() throws Exception {
            var response = get("/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            var match = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
            assertThat(match.find()).isTrue();
            return match.group(1);
        }

        String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue();
        }
    }
}
