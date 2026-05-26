CREATE TABLE chess_matches (
                               id BIGSERIAL PRIMARY KEY,
                               white_player_id BIGINT NOT NULL,
                               black_player_id BIGINT NOT NULL,
                               winner_player_id BIGINT,
                               win_reason VARCHAR(50),
                               pgn VARCHAR(2000),
                               created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                               ended_at TIMESTAMP WITHOUT TIME ZONE
);