package edu.rutmiit.enterprise.matchmaking.service;

import edu.rutmiit.enterprise.matchmaking.api.ApiException;
import edu.rutmiit.enterprise.matchmaking.api.CreateLobbyRequest;
import edu.rutmiit.enterprise.matchmaking.api.CreatePlayerRequest;
import edu.rutmiit.enterprise.matchmaking.api.DiagnosticsResponse;
import edu.rutmiit.enterprise.matchmaking.api.LobbyResponse;
import edu.rutmiit.enterprise.matchmaking.api.PlayerResponse;
import edu.rutmiit.enterprise.matchmaking.domain.LobbyEntity;
import edu.rutmiit.enterprise.matchmaking.domain.PlayerEntity;
import edu.rutmiit.enterprise.matchmaking.repository.LobbyRepository;
import edu.rutmiit.enterprise.matchmaking.repository.PlayerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MatchmakingService {
    private static final String UNKNOWN_REGION = "ZZ";

    private final PlayerRepository playerRepository;
    private final LobbyRepository lobbyRepository;

    public MatchmakingService(PlayerRepository playerRepository, LobbyRepository lobbyRepository) {
        this.playerRepository = playerRepository;
        this.lobbyRepository = lobbyRepository;
    }

    @Transactional
    public PlayerResponse createPlayer(CreatePlayerRequest request) {
        String nickname = request.nickname().trim();
        if (playerRepository.existsByNickname(nickname)) {
            throw new ApiException(HttpStatus.CONFLICT, "Игрок с таким никнеймом уже существует");
        }
        String region = request.region() == null ? UNKNOWN_REGION : request.region();
        PlayerEntity player = playerRepository.save(
                new PlayerEntity(UUID.randomUUID(), nickname, region)
        );
        return toResponse(player);
    }

    @Transactional(readOnly = true)
    public List<PlayerResponse> players() {
        return playerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public LobbyResponse createLobby(CreateLobbyRequest request) {
        if (lobbyRepository.existsByCode(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Лобби с таким кодом уже существует");
        }
        PlayerEntity host = playerRepository.findById(request.hostId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Игрок не найден"));
        LobbyEntity lobby = lobbyRepository.save(new LobbyEntity(
                UUID.randomUUID(),
                request.name().trim(),
                request.code(),
                host,
                request.maxPlayers()
        ));
        return toResponse(lobby);
    }

    @Transactional(readOnly = true)
    public List<LobbyResponse> lobbies() {
        return lobbyRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LobbyResponse lobby(UUID id) {
        LobbyEntity lobby = lobbyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Лобби не найдено"));
        return toResponse(lobby);
    }

    @Transactional(readOnly = true)
    public DiagnosticsResponse diagnostics() {
        return new DiagnosticsResponse(playerRepository.count(), lobbyRepository.count());
    }

    private PlayerResponse toResponse(PlayerEntity player) {
        return new PlayerResponse(player.getId(), player.getNickname(), player.getRegion());
    }

    private LobbyResponse toResponse(LobbyEntity lobby) {
        return new LobbyResponse(
                lobby.getId(),
                lobby.getName(),
                lobby.getCode(),
                lobby.getHost().getId(),
                lobby.getHost().getNickname(),
                lobby.getMaxPlayers(),
                lobby.getStatus()
        );
    }
}
