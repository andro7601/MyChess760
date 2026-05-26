package com.chess.services;

import com.chess.api.auth.dtos.AuthResponseDto;
import com.chess.models.entity.PlayerModel;
import com.chess.repositories.PlayerRepository;
import com.chess.services.auth.AuthService;
import com.chess.services.auth.jwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private jwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_savesPlayerAndReturnsToken() {
        when(passwordEncoder.encode("rawpass")).thenReturn("hashedpass");
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        AuthResponseDto response = authService.register("andria", "rawpass");

        assertEquals("jwt-token", response.token());
        verify(playerRepository).save(any(PlayerModel.class));
        verify(passwordEncoder).encode("rawpass");
    }
}
