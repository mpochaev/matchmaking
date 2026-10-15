package edu.rutmiit.enterprise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BffControllerTest {
    @Test
    void csrfEndpointReadsTokenFromWebExchangeAttribute() {
        CsrfToken token = mock(CsrfToken.class);
        when(token.getHeaderName()).thenReturn("X-CSRF-TOKEN");
        when(token.getParameterName()).thenReturn("_csrf");
        when(token.getToken()).thenReturn("example-token");

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/bff/csrf"));
        exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(token));

        Map<String, String> body = new BffController().csrf(exchange).block();

        assertThat(body).containsEntry("headerName", "X-CSRF-TOKEN")
                .containsEntry("parameterName", "_csrf")
                .containsEntry("token", "example-token");
    }

    @Test
    void meReturnsReadableOidcUsernameInsteadOfSubjectId() {
        Authentication authentication = mock(Authentication.class);
        OidcUser user = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(user.getPreferredUsername()).thenReturn("operator");

        assertThat(new BffController().me(authentication).block())
                .containsEntry("name", "operator");
    }
}
