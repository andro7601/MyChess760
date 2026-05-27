package com.chess.middleware;

import com.chess.models.entity.PlayerModel;
import com.chess.repositories.PlayerRepository;
import com.chess.services.auth.jwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class jwtFilter extends OncePerRequestFilter {

    private final jwtService jwtService;
    private final PlayerRepository playerRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getRequestURI().startsWith("/ws")) {
            token = request.getParameter("token");
        }

        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        Long id = jwtService.extractId(token);

        if (id != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            PlayerModel player =playerRepository.findById(id)
                    .orElseThrow();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(player, null, player.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
