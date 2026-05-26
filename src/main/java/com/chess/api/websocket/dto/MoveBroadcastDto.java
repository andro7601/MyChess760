package com.chess.api.websocket.dto;

public record MoveBroadcastDto(
        String move,
        String turnOwner,
        boolean isGameOver,
        String winReason
) {
}