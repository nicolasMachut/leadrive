CREATE TABLE reset_password_token (
    id_reset_password_token BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    expire_le TIMESTAMP NOT NULL,
    utilise_le TIMESTAMP,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP
);
CREATE INDEX idx_reset_token_token ON reset_password_token(token);
