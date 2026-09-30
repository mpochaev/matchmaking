CREATE TABLE players (
    id UUID PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE lobbies (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    host_id UUID NOT NULL REFERENCES players(id),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_lobbies_host_id ON lobbies(host_id);
