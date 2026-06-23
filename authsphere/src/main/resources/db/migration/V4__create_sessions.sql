CREATE TABLE sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_family_id UUID NOT NULL,
    device_name             VARCHAR(255),
    browser                 VARCHAR(100),
    os                      VARCHAR(100),
    ip_address              VARCHAR(45),
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    login_time              TIMESTAMP NOT NULL DEFAULT now(),
    last_activity           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_user   ON sessions(user_id);
CREATE INDEX idx_sessions_active ON sessions(active);
