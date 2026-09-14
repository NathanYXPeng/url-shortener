CREATE TABLE short_url (
    id            BIGSERIAL PRIMARY KEY,
    short_code    VARCHAR(10) NOT NULL UNIQUE,
    original_url  TEXT NOT NULL,
    click_count   BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_short_url_short_code ON short_url (short_code);
