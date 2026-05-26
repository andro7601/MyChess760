package com.chess.services.auth;

import com.chess.api.auth.dtos.AuthResponseDto;
import com.chess.models.entity.PlayerModel;
import com.chess.repositories.PlayerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final jwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PlayerRepository playerRepository;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponseDto register(
            String username,
            String password
    ){
        PlayerModel playerModel = new PlayerModel();
        playerModel.setUsername(username);
        playerModel.setPassword(passwordEncoder.encode(password));
        playerRepository.save(playerModel);
        return new AuthResponseDto(jwtService.generateToken(playerModel));
    }

    @Transactional
    public AuthResponseDto login(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        PlayerModel player = playerRepository.findByUsername(username)
                .orElseThrow();
        return new AuthResponseDto(jwtService.generateToken(player));
    }

}
