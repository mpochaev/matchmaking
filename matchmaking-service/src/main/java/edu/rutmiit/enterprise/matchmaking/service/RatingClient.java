package edu.rutmiit.enterprise.matchmaking.service;

import edu.rutmiit.enterprise.matchmaking.api.RatingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver.principal;

@Service
@Profile("!session-auth")
public class RatingClient {
    private static final Logger log = LoggerFactory.getLogger(RatingClient.class);
    private final RestClient ratingRestClient;

    public RatingClient(RestClient ratingRestClient) {
        this.ratingRestClient = ratingRestClient;
    }

    public RatingResponse rate(UUID playerId) {
        log.info("Calling rating-service playerId={}", playerId);
        return ratingRestClient.get()
                .uri("/internal/ratings/{playerId}", playerId)
                .attributes(clientRegistrationId("rating"))
                .attributes(principal("matchmaking-service"))
                .retrieve()
                .body(RatingResponse.class);
    }
}
