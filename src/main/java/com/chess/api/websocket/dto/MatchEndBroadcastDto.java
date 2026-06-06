package com.chess.api.websocket.dto;

public record MatchEndBroadcastDto(
        String type,
        String reason,
        Long winnerId,
        String move
) implements GameBroadcast{
}
