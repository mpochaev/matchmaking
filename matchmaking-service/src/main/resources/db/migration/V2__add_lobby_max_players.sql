ALTER TABLE lobbies
    ADD COLUMN max_players INTEGER NULL;

ALTER TABLE lobbies
    ADD CONSTRAINT chk_lobbies_max_players
        CHECK (max_players BETWEEN 3 AND 5);
