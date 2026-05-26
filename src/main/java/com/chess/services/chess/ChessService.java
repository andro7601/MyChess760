package com.chess.services.chess;

import com.chess.api.websocket.dto.MoveBroadcastDto;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.chess.models.entity.ChessMatchModel;
import com.chess.models.dto.MatchSnapshot;
import com.chess.repositories.ChessMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChessService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChessMatchRepository chessMatchRepository;
    private static final String KEY_PREFIX = "match:";

    public MoveBroadcastDto handlePlayerMove(String matchId, Long playerId, String moveUci) {
        String redisKey = KEY_PREFIX + matchId;

        MatchSnapshot snapshot = (MatchSnapshot) redisTemplate.opsForValue().get(redisKey);
        if (snapshot == null) return null;


        Long expectedPlayerId = snapshot.getTurnOwner().equals("WHITE")
                ? snapshot.getWhitePlayerId()
                : snapshot.getBlackPlayerId();
        if (!playerId.equals(expectedPlayerId)) return null;

        try {

            Board board = new Board();
            board.loadFromFen(snapshot.getFen());


            Move move = null;
            for (Move legalMove : board.legalMoves()) {
                if (legalMove.toString().equals(moveUci)) {
                    move = legalMove;
                    break;
                }
            }
            if (move == null) return null;

            board.doMove(move);

            String updatedPgn = (snapshot.getPgn() == null || snapshot.getPgn().isEmpty())
                    ? moveUci
                    : snapshot.getPgn() + " " + moveUci;
            snapshot.setPgn(updatedPgn);


            if(board.isMated() || board.isDraw() || board.isStaleMate()){
                finalizeMatch(matchId,snapshot,board);
                String reason=null;
                if(board.isMated()){reason="Mated";}
                if(board.isDraw()){reason="Draw";}
                if(board.isStaleMate()){reason="Stale Mate";}
                return new MoveBroadcastDto(moveUci,null,true,reason);
            }

            String nextTurnColor = board.getSideToMove().toString();


            snapshot.setFen(board.getFen());
            snapshot.setTurnOwner(board.getSideToMove().toString());
            redisTemplate.opsForValue().set(redisKey, snapshot, 2, TimeUnit.HOURS);
            return  new MoveBroadcastDto(moveUci,nextTurnColor,false,null);

        } catch (Exception e) {
            return null;
        }
    }

    private void finalizeMatch(String matchId, MatchSnapshot snapshot, Board board) {
        ChessMatchModel completedMatch = new ChessMatchModel();
        completedMatch.setWhitePlayerId(snapshot.getWhitePlayerId());
        completedMatch.setBlackPlayerId(snapshot.getBlackPlayerId());
        completedMatch.setCreatedAt(LocalDateTime.now());
        completedMatch.setEndedAt(LocalDateTime.now());

        completedMatch.setPgn(snapshot.getPgn());

        if (board.isMated()) {
            completedMatch.setWinReason(ChessMatchModel.WinReason.CHECKMATE);
            Long winnerId = board.getSideToMove().toString().equals("WHITE")
                    ? snapshot.getBlackPlayerId()
                    : snapshot.getWhitePlayerId();
            completedMatch.setWinnerPlayerId(winnerId);
        } else if (board.isStaleMate()) {
            completedMatch.setWinReason(ChessMatchModel.WinReason.DRAW_STALEMATE);
        } else {
            completedMatch.setWinReason(ChessMatchModel.WinReason.DRAW_MUTUAL);
        }

        chessMatchRepository.save(completedMatch);
        redisTemplate.delete(KEY_PREFIX + matchId);
    }
}