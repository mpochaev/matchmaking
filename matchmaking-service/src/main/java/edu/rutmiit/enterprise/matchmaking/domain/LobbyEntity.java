package edu.rutmiit.enterprise.matchmaking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "lobbies")
public class LobbyEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private PlayerEntity host;

    @Column(name = "max_players")
    private Integer maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LobbyStatus status;

    @Version
    private long version;

    protected LobbyEntity() {
    }

    public LobbyEntity(UUID id, String name, String code, PlayerEntity host, Integer maxPlayers) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.host = host;
        this.maxPlayers = maxPlayers;
        this.status = LobbyStatus.WAITING;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public PlayerEntity getHost() {
        return host;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public LobbyStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
