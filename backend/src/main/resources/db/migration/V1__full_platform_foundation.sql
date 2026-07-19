CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    normalized_username VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    encoded_password VARCHAR(255) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    account_status VARCHAR(24) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_sessions (
    id VARCHAR(128) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE matches (
    id UUID PRIMARY KEY,
    room_code VARCHAR(12) UNIQUE,
    mode VARCHAR(24) NOT NULL DEFAULT 'CASUAL',
    timer_mode VARCHAR(32) NOT NULL DEFAULT 'CASUAL_UNTIMED',
    phase VARCHAR(24) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    terminal_at TIMESTAMPTZ
);

CREATE TABLE match_players (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    side VARCHAR(24) NOT NULL,
    user_id UUID REFERENCES users(id),
    player_key VARCHAR(128) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (match_id, side),
    UNIQUE (match_id, player_key)
);

CREATE TABLE match_formations (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    side VARCHAR(24) NOT NULL,
    piece_id UUID NOT NULL,
    public_piece_id UUID NOT NULL,
    rank VARCHAR(32) NOT NULL,
    row_number SMALLINT,
    column_number SMALLINT,
    alive BOOLEAN NOT NULL,
    locked BOOLEAN NOT NULL,
    PRIMARY KEY (match_id, piece_id),
    UNIQUE (match_id, public_piece_id)
);

CREATE TABLE match_snapshots (
    id BIGSERIAL PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    aggregate_version BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (match_id, aggregate_version)
);

CREATE TABLE match_events (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    event_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (match_id, sequence_number)
);

CREATE TABLE match_commands (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    command_id UUID NOT NULL,
    fingerprint TEXT NOT NULL,
    result_json TEXT NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (match_id, command_id)
);

CREATE TABLE match_chat_messages (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    sequence_number BIGINT NOT NULL,
    body VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (match_id, sequence_number)
);

CREATE TABLE user_blocks (
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE TABLE player_reports (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES users(id),
    reported_user_id UUID NOT NULL REFERENCES users(id),
    match_id UUID REFERENCES matches(id),
    category VARCHAR(40) NOT NULL,
    comment VARCHAR(1000),
    chat_message_references TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (reporter_id <> reported_user_id)
);

CREATE TABLE rating_seasons (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE player_ratings (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    season_id UUID NOT NULL REFERENCES rating_seasons(id),
    rating INTEGER NOT NULL DEFAULT 1200,
    rated_games INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    draws INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, season_id)
);

CREATE TABLE rating_changes (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES matches(id),
    user_id UUID NOT NULL REFERENCES users(id),
    season_id UUID NOT NULL REFERENCES rating_seasons(id),
    pre_match_rating INTEGER NOT NULL,
    expected_score NUMERIC(8, 6) NOT NULL,
    actual_score NUMERIC(2, 1) NOT NULL,
    rating_delta INTEGER NOT NULL,
    post_match_rating INTEGER NOT NULL,
    k_factor INTEGER NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (match_id, user_id)
);

CREATE INDEX idx_verification_user_expiry ON email_verification_tokens(user_id, expires_at);
CREATE INDEX idx_reset_user_expiry ON password_reset_tokens(user_id, expires_at);
CREATE INDEX idx_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_matches_phase_updated ON matches(phase, updated_at);
CREATE INDEX idx_match_players_user ON match_players(user_id, match_id);
CREATE INDEX idx_match_events_match ON match_events(match_id, sequence_number);
CREATE INDEX idx_chat_match_sequence ON match_chat_messages(match_id, sequence_number);
CREATE INDEX idx_reports_reported_created ON player_reports(reported_user_id, created_at);
CREATE INDEX idx_ratings_leaderboard ON player_ratings(season_id, rating DESC, rated_games DESC, user_id);

INSERT INTO rating_seasons (id, name, starts_at, active)
VALUES ('00000000-0000-4000-8000-000000000001', 'Season One', CURRENT_TIMESTAMP, TRUE);
