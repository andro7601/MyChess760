package com.chess.api.auth.dtos;

public record RegisterRequestDto(
        String username,
        String password
) {}