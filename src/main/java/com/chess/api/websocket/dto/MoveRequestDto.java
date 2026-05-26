package com.chess.api.websocket.dto;

import lombok.AllArgsConstructor;

public record MoveRequestDto(
        Long playerId,
        String move
) {
}
