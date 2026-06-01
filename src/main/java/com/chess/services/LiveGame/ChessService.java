package com.chess.services.LiveGame;

import com.chess.api.websocket.dto.GameBroadcast;
import com.chess.api.websocket.dto.MatchEndBroadcastDto;
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

    public GameBroadcast handlePlayerMove(String matchId, Long playerId, String moveUci) {
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

            if (board.isMated() || board.isDraw() || board.isStaleMate()) {

                Boolean handlesFinalization = redisTemplate.delete(redisKey);

                if (Boolean.TRUE.equals(handlesFinalization)) {

                    String reason = null;
                    if (board.isMated()) { reason = "CHECKMATE"; }
                    if (board.isDraw()) { reason = "DRAW"; }
                    if (board.isStaleMate()) { reason = "STALEMATE"; }
                    finalizeMatch(snapshot, reason);
                    Long winnerId = board.getSideToMove().toString().equals("WHITE")
                            ? snapshot.getBlackPlayerId()
                            : snapshot.getWhitePlayerId();
                    return new MatchEndBroadcastDto("END",reason,winnerId);
                } else {
                    return null;
                }
            }

            String nextTurnColor = board.getSideToMove().toString();


            snapshot.setFen(board.getFen());
            snapshot.setTurnOwner(board.getSideToMove().toString());
            redisTemplate.opsForValue().set(redisKey, snapshot, 2, TimeUnit.HOURS);
            return  new MoveBroadcastDto("MOVE",moveUci,nextTurnColor);

        } catch (Exception e) {
            return null;
        }
    }

    public void finalizeMatch(MatchSnapshot snapshot, String reason) {
        ChessMatchModel completedMatch = new ChessMatchModel();
        completedMatch.setWhitePlayerId(snapshot.getWhitePlayerId());
        completedMatch.setBlackPlayerId(snapshot.getBlackPlayerId());
        completedMatch.setCreatedAt(LocalDateTime.now());
        completedMatch.setEndedAt(LocalDateTime.now());

        completedMatch.setPgn(snapshot.getPgn());
        if(snapshot.getWhiteTimeRemaining()==-1 ||  snapshot.getBlackTimeRemaining()==-1){
            if(snapshot.getWhiteTimeRemaining()==-1)completedMatch.setWinReason(ChessMatchModel.WinReason.ABANDON_WHITE);
            else completedMatch.setWinReason(ChessMatchModel.WinReason.ABANDON_BLACK);
            completedMatch.setWinnerPlayerId(snapshot.getWhiteTimeRemaining()==-1 ? snapshot.getBlackPlayerId() : snapshot.getWhitePlayerId());
        }
        else if(snapshot.getWhiteTimeRemaining()==0 ||  snapshot.getBlackTimeRemaining()==0){
            completedMatch.setWinReason(ChessMatchModel.WinReason.TIMEOUT);
            completedMatch.setWinnerPlayerId(snapshot.getWhiteTimeRemaining()==0 ? snapshot.getBlackPlayerId() : snapshot.getWhitePlayerId());
        }
        else if (reason.equals("CHECKMATE")) {
            completedMatch.setWinReason(ChessMatchModel.WinReason.CHECKMATE);
            Long winnerId = snapshot.getTurnOwner().equals("WHITE")?snapshot.getWhitePlayerId():snapshot.getBlackPlayerId();
            completedMatch.setWinnerPlayerId(winnerId);
        } else if (reason.equals("STALEMATE")) {
            completedMatch.setWinReason(ChessMatchModel.WinReason.DRAW_STALEMATE);
        } else {
            completedMatch.setWinReason(ChessMatchModel.WinReason.DRAW_MUTUAL);
        }

        chessMatchRepository.save(completedMatch);
    }
}