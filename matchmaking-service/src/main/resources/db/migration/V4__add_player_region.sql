ALTER TABLE players
    ADD COLUMN region VARCHAR(2) NULL;

ALTER TABLE players
    ADD CONSTRAINT chk_players_region
        CHECK (region IN ('RU', 'EU', 'AS', 'US', 'ZZ'));
