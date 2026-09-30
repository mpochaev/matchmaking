package edu.rutmiit.enterprise.matchmaking.api;

import java.util.UUID;

public record PlayerResponse(UUID id, String nickname, String region) {
}
