package com.chess.api.websocket;

import com.chess.api.websocket.dto.MoveBroadcastDto;
import com.chess.api.websocket.dto.MoveRequestDto;
import com.chess.models.dto.MatchSnapshot;
import com.chess.models.entity.PlayerModel;
import com.chess.repositories.PlayerRepository;
import com.chess.services.auth.SecurityService;
import com.chess.services.chess.ChessService;
import com.chess.services.matchmaking.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ChessService chessService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchmakingService matchmakingService;
    private final PlayerRepository playerRepository;
    private final SecurityService securityService;

    @MessageMapping("/match/{matchId}/move")
    public void handleMove(@DestinationVariable String matchId, MoveRequestDto request, Principal principal) {

        Authentication authentication=(Authentication) principal;
        PlayerModel player= (PlayerModel) authentication.getPrincipal();
        MoveBroadcastDto result = chessService.handlePlayerMove(matchId, player.getId(), request.move());

        if (result == null) {
            return;
        }
        String destination = "/sub/match/" + matchId;
        messagingTemplate.convertAndSend(destination, result);
    }

    @MessageMapping("/matchmaking/join")
    public void handleJoinQueue(Principal principal) {
        PlayerModel player=securityService.getPlayer();
        MatchSnapshot match = matchmakingService.joinQueue(player);

        if (match != null) {
            sendMatchToPlayer(match.getWhitePlayerId(), match);
            sendMatchToPlayer(match.getBlackPlayerId(), match);
        }
    }

    @MessageMapping("/matchmaking/cancel")
    public void handleCancelQueue(Principal principal) {
        Authentication authentication = (Authentication) principal;
        PlayerModel player = (PlayerModel) authentication.getPrincipal();
        matchmakingService.leaveQueue(player.getId());
    }

    @MessageMapping("/matchmaking/reconnect")
    public void Reconnect(Principal principal) {
        Authentication authentication = (Authentication) principal;
        PlayerModel player = (PlayerModel) authentication.getPrincipal();
        MatchSnapshot match=matchmakingService.Reconnect(player.getId());
        if(match==null){
            return ;
        }
        messagingTemplate.convertAndSendToUser(player.getUsername(), "/sub/queue", match);
    }

    private void sendMatchToPlayer(Long playerId, MatchSnapshot match) {
        playerRepository.findById(playerId)
                .map(PlayerModel::getUsername)
                .ifPresent(username -> messagingTemplate.convertAndSendToUser(username, "/sub/queue", match));
    }
}
