package edu.rutmiit.enterprise.matchmaking.api;

import edu.rutmiit.enterprise.matchmaking.service.MatchmakingService;
import edu.rutmiit.enterprise.matchmaking.service.RatingClient;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("!session-auth")
@RequestMapping("/api/players")
public class RatingController {
    private final MatchmakingService players;
    private final RatingClient ratings;

    public RatingController(MatchmakingService players, RatingClient ratings) {
        this.players = players;
        this.ratings = ratings;
    }

    @GetMapping("/{id}/rating")
    @PreAuthorize("hasAnyRole('PLAYER','OPERATOR')")
    public RatingResponse rating(@PathVariable UUID id) {
        players.player(id);
        return ratings.rate(id);
    }
}
