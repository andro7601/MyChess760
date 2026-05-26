package com.chess.api.websocket;

import com.chess.api.websocket.dto.MoveBroadcastDto;
import com.chess.api.websocket.dto.MoveRequestDto;
import com.chess.services.chess.ChessService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ChessService chessService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/match/{matchId}/move")
    public void handleMove(@DestinationVariable String matchId, MoveRequestDto request) {


        MoveBroadcastDto result = chessService.handlePlayerMove(matchId, request.playerId(), request.move());

        if (result == null) {
            return;
        }
        String destination = "/sub/match/" + matchId;

    }
}