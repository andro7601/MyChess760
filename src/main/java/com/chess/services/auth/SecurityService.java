package com.chess.services.auth;

import com.chess.models.PlayerModel;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {
    public PlayerModel getPlayer() {
        return (PlayerModel) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    public Long getPlayerId() {
        return getPlayer().getId();
    }

    public String getUsername() {
        return getPlayer().getUsername();
    }
}