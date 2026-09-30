package edu.rutmiit.enterprise.matchmaking.api;

import edu.rutmiit.enterprise.matchmaking.domain.LobbyStatus;

import java.util.UUID;

public record LobbyResponse(
        UUID id,
        String name,
        String code,
        UUID hostId,
        String hostNickname,
        Integer maxPlayers,
        LobbyStatus status
) {
}
