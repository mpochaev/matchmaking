ALTER TABLE players
    ALTER COLUMN region SET DEFAULT 'ZZ';

UPDATE players
SET region = 'ZZ'
WHERE region IS NULL;

ALTER TABLE players
    ALTER COLUMN region SET NOT NULL;
