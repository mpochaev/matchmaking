package edu.rutmiit.enterprise.rating.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/internal")
public class RatingController {
    @GetMapping("/ratings/{playerId}")
    @PreAuthorize("hasRole('SERVICE')")
    public RatingResponse rating(@PathVariable UUID playerId) {
        return new RatingResponse(playerId, 1500, "GOLD");
    }

    @GetMapping("/admin/info")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String, String> info() {
        return Map.of("service", "rating-service", "status", "ok");
    }

    public record RatingResponse(UUID playerId, int rating, String rank) {}
}
