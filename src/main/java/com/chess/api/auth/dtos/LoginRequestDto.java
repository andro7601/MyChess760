package com.chess.api.auth.dtos;

public record LoginRequestDto(
        String username,
        String password
) {
}
