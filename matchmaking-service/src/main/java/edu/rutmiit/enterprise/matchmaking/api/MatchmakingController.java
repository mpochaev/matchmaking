package edu.rutmiit.enterprise.matchmaking.api;

import edu.rutmiit.enterprise.matchmaking.service.MatchmakingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MatchmakingController {
    private final MatchmakingService matchmakingService;

    public MatchmakingController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @GetMapping("/public/status")
    Map<String, String> status() {
        return Map.of("service", "matchmaking-service", "status", "UP");
    }

    @GetMapping("/players")
    @PreAuthorize("hasAnyRole('PLAYER','OPERATOR')")
    List<PlayerResponse> players() {
        return matchmakingService.players();
    }

    @PostMapping("/players")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERATOR')")
    PlayerResponse createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        return matchmakingService.createPlayer(request);
    }

    @GetMapping("/lobbies")
    @PreAuthorize("hasAnyRole('PLAYER','OPERATOR')")
    List<LobbyResponse> lobbies() {
        return matchmakingService.lobbies();
    }

    @GetMapping("/lobbies/{id}")
    @PreAuthorize("hasAnyRole('PLAYER','OPERATOR')")
    LobbyResponse lobby(@PathVariable UUID id) {
        return matchmakingService.lobby(id);
    }

    @PostMapping("/lobbies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLAYER','OPERATOR')")
    LobbyResponse createLobby(@Valid @RequestBody CreateLobbyRequest request) {
        return matchmakingService.createLobby(request);
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('OPERATOR')")
    DiagnosticsResponse diagnostics() {
        return matchmakingService.diagnostics();
    }
}
