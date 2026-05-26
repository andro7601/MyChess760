package com.chess.services.chess;

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

    public boolean handlePlayerMove(String matchId, Long playerId, String moveUci) {
        String redisKey = KEY_PREFIX + matchId;

        // 1. Grab snapshot from Redis
        MatchSnapshot snapshot = (MatchSnapshot) redisTemplate.opsForValue().get(redisKey);
        if (snapshot == null) return false;


        Long expectedPlayerId = snapshot.getTurnOwner().equals("WHITE")
                ? snapshot.getWhitePlayerId()
                : snapshot.getBlackPlayerId();
        if (!playerId.equals(expectedPlayerId)) return false;

        try {

            Board board = new Board();
            board.loadFromFen(snapshot.getFen());


            Move move = new Move(moveUci, board.getSideToMove());
            if (!board.isMoveLegal(move, true)) return false;


            board.doMove(move);

            String updatedPgn = (snapshot.getPgn() == null || snapshot.getPgn().isEmpty())
                    ? moveUci
                    : snapshot.getPgn() + " " + moveUci;
            snapshot.setPgn(updatedPgn);


            if (board.isMated() || board.isDraw() || board.isStaleMate()) {
                finalizeMatch(matchId, snapshot, board);
                return true;
            }
            snapshot.setFen(board.getFen());
            snapshot.setTurnOwner(board.getSideToMove().toString());

            redisTemplate.opsForValue().set(redisKey, snapshot, 2, TimeUnit.HOURS);
            return true;

        } catch (Exception e) {
            return false;
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