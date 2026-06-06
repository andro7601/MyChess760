package com.chess.api.websocket.dto;

public record MoveBroadcastDto (
        String type,
        String move,
        String turnOwner,
        long whiteTimeRemaining,
        long blackTimeRemaining
) implements GameBroadcast {}