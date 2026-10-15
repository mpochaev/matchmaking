package edu.rutmiit.enterprise.gateway;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
public class BffController {
    @GetMapping("/bff/csrf")
    Mono<Map<String, String>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        return token.map(value -> Map.of(
                "headerName", value.getHeaderName(),
                "parameterName", value.getParameterName(),
                "token", value.getToken()
        ));
    }

    @GetMapping("/bff/me")
    Mono<Map<String, String>> me(Authentication authentication) {
        String name = authentication.getPrincipal() instanceof OidcUser user
                ? user.getPreferredUsername() : authentication.getName();
        return Mono.just(Map.of("name", name));
    }
}
