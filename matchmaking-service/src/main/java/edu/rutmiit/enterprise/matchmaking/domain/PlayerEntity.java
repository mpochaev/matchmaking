package edu.rutmiit.enterprise.matchmaking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "players")
public class PlayerEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String nickname;

    @Column(nullable = false, length = 2)
    private String region;

    @Version
    private long version;

    protected PlayerEntity() {
    }

    public PlayerEntity(UUID id, String nickname, String region) {
        this.id = id;
        this.nickname = nickname;
        this.region = region;
    }

    public UUID getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRegion() {
        return region;
    }
}
