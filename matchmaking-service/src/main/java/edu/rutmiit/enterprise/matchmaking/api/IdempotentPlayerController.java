package edu.rutmiit.enterprise.matchmaking.api;

import edu.rutmiit.enterprise.matchmaking.service.IdempotentPlayerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
public class IdempotentPlayerController {
    private final IdempotentPlayerService players;

    public IdempotentPlayerController(IdempotentPlayerService players) {
        this.players = players;
    }

    @PostMapping("/api/players/idempotent")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<PlayerResponse> create(Principal principal,
            @RequestHeader("Idempotency-Key") String rawKey, @Valid @RequestBody CreatePlayerRequest request) {
        if (!rawKey.matches("(?i)^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key должен быть полным UUID без кавычек");
        }
        var result = players.create(principal.getName(), UUID.fromString(rawKey), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.player());
    }
}
