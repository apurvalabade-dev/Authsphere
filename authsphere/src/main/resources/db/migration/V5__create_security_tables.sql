CREATE TABLE failed_login_attempts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ip_address   VARCHAR(45),
    attempted_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_failed_attempts_user ON failed_login_attempts(user_id);
