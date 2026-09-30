package edu.rutmiit.enterprise.matchmaking.repository;

import edu.rutmiit.enterprise.matchmaking.domain.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {
    boolean existsByNickname(String nickname);
}
