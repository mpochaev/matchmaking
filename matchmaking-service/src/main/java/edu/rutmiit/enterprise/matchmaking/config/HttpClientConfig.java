package edu.rutmiit.enterprise.matchmaking.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@Profile("!session-auth")
public class HttpClientConfig {
    @Bean
    OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient ratingRestClient(RestClient.Builder builder,
            OAuth2AuthorizedClientManager manager,
            @Value("${app.rating.base-url}") String baseUrl) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        var oauth = new OAuth2ClientHttpRequestInterceptor(manager);
        oauth.setPrincipalResolver(new RequestAttributePrincipalResolver());

        return builder.baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get("requestId");
                    if (requestId != null) {
                        request.getHeaders().set(RequestIds.HEADER, requestId);
                    }
                    return execution.execute(request, body);
                })
                .requestInterceptor(oauth)
                .build();
    }
}
