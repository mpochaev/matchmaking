package edu.rutmiit.enterprise.matchmaking.api;

import java.util.UUID;

public record RatingResponse(UUID playerId, int rating, String rank) {}
