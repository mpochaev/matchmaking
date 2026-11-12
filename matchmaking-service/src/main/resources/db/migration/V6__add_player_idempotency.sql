CREATE TABLE player_idempotency (
    principal_id VARCHAR(200) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    operation_key UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    player_id UUID,
    response_nickname VARCHAR(100),
    response_region VARCHAR(2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (clock_timestamp() + INTERVAL '24 hours'),
    PRIMARY KEY (principal_id, operation, operation_key),
    CHECK ((state = 'IN_PROGRESS' AND player_id IS NULL AND response_nickname IS NULL AND response_region IS NULL)
        OR (state = 'COMPLETED' AND player_id IS NOT NULL AND response_nickname IS NOT NULL AND response_region IS NOT NULL))
);

-- Снимок ответа сохраняется независимо от будущих изменений/удаления игрока.
-- Истёкший ключ отклоняется с 410, не освобождается автоматически для нового эффекта.
CREATE INDEX idx_player_idempotency_expiry ON player_idempotency(expires_at);
