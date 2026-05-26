package com.chess.api.auth.dtos;

public record AuthResponseDto(
        String token,
        Long id,
        String username
) {
}
