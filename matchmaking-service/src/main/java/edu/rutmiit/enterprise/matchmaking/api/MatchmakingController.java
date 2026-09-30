package edu.rutmiit.enterprise.matchmaking.api;

import edu.rutmiit.enterprise.matchmaking.service.MatchmakingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MatchmakingController {
    private final MatchmakingService matchmakingService;

    public MatchmakingController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @GetMapping("/players")
    List<PlayerResponse> players() {
        return matchmakingService.players();
    }

    @PostMapping("/players")
    @ResponseStatus(HttpStatus.CREATED)
    PlayerResponse createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        return matchmakingService.createPlayer(request);
    }

    @GetMapping("/lobbies")
    List<LobbyResponse> lobbies() {
        return matchmakingService.lobbies();
    }

    @GetMapping("/lobbies/{id}")
    LobbyResponse lobby(@PathVariable UUID id) {
        return matchmakingService.lobby(id);
    }

    @PostMapping("/lobbies")
    @ResponseStatus(HttpStatus.CREATED)
    LobbyResponse createLobby(@Valid @RequestBody CreateLobbyRequest request) {
        return matchmakingService.createLobby(request);
    }
}
