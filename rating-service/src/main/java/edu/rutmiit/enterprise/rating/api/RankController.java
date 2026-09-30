package edu.rutmiit.enterprise.rating.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class RankController {
    @GetMapping("/ranks")
    @PreAuthorize("hasAnyRole('PLAYER','OPERATOR')")
    public List<RankResponse> ranks() {
        return List.of(
                new RankResponse("BRONZE", 0),
                new RankResponse("SILVER", 1000),
                new RankResponse("GOLD", 1500),
                new RankResponse("PLATINUM", 2000),
                new RankResponse("DIAMOND", 2500)
        );
    }

    public record RankResponse(String rank, int minRating) {}
}
