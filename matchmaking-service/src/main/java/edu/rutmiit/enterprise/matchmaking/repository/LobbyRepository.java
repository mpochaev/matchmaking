package edu.rutmiit.enterprise.matchmaking.repository;

import edu.rutmiit.enterprise.matchmaking.domain.LobbyEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LobbyRepository extends JpaRepository<LobbyEntity, UUID> {
    boolean existsByCode(String code);

    @Override
    @EntityGraph(attributePaths = "host")
    List<LobbyEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "host")
    Optional<LobbyEntity> findById(UUID id);
}
