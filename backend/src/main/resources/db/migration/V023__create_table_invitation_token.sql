CREATE TABLE invitation_token (
    id_invitation_token BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    expire_le TIMESTAMP NOT NULL,
    utilise_le TIMESTAMP,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP
);
CREATE INDEX idx_invitation_token_token ON invitation_token(token);
CREATE INDEX idx_invitation_token_user ON invitation_token(user_id);
