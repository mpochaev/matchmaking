package edu.rutmiit.enterprise.matchmaking.service;

import edu.rutmiit.enterprise.matchmaking.api.RatingResponse;
import edu.rutmiit.enterprise.matchmaking.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.http.HttpStatus;

import java.util.UUID;


@Service
@Profile("!session-auth")
public class RatingClient {
    private static final Logger log = LoggerFactory.getLogger(RatingClient.class);
    private final RestClient ratingRestClient;
    private final RatingRetry retry;
    private final OAuth2AuthorizedClientManager clients;

    public RatingClient(RestClient ratingRestClient, RatingRetry retry,
                                OAuth2AuthorizedClientManager clients) {
        this.ratingRestClient = ratingRestClient;
        this.retry = retry;
        this.clients = clients;
    }

    public RatingResponse rate(UUID playerId) {
        log.info("Calling rating-service playerId={}", playerId);
        try {
            // Получение/обновление токена не повторяется вместе с бизнес-GET.
            var client = clients.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("rating")
                    .principal("matchmaking-service").build());
            if (client == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Не удалось получить сервисную идентичность");
            }
            return retry.execute(() -> {
                var response = ratingRestClient.get()
                .uri("/internal/ratings/{playerId}", playerId)
                .headers(headers -> headers.setBearerAuth(client.getAccessToken().getTokenValue()))
                .retrieve()
                .body(RatingResponse.class);
                if (response == null) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Пустой ответ сервиса рейтингов");
                }
                return response;
            });
        } catch (TimeBudget.Exhausted exhausted) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, exhausted.getMessage());
        } catch (RestClientException | OAuth2AuthorizationException failure) {
            String result = HttpFailures.result(failure);
            log.atWarn().addKeyValue("event", "rating.unavailable")
                    .addKeyValue("outcome", result).log("Рейтинг недоступен");
            throw new ApiException(result.equals("timeout") ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                    "Сервис рейтингов недоступен; причина: " + result);
        }
    }
}
