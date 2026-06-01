package com.chess.services.auth;

import com.chess.models.entity.PlayerModel;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.Principal;

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

    public PlayerModel getPlayer(Principal principal){
        Authentication authentication = (Authentication) principal;
        PlayerModel player = (PlayerModel) authentication.getPrincipal();
        return player;
    }
    public Long getPlayerId(Principal principal){
        return getPlayer(principal).getId();
    }
    public String getUsername(Principal principal){
        return getPlayer(principal).getUsername();
    }
}