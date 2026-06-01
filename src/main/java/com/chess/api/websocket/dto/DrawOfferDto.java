package com.chess.api.websocket.dto;

public record DrawOfferDto(
        String type,
        Long offererId
)implements  GameBroadcast {
}
