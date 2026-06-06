package com.chess.services.LiveGame;

import com.chess.api.websocket.dto.DrawOfferDto;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChessService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChessMatchRepository chessMatchRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String KEY_PREFIX = "match:";
    private static final String DRAW_OFFER_PREFIX = "draw_offer:";

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
            long now = System.currentTimeMillis();
            long elapsed = now - snapshot.getTurnStartTime();
            if ("WHITE".equals(snapshot.getTurnOwner())) {
                snapshot.setWhiteTimeRemaining(Math.max(0L, snapshot.getWhiteTimeRemaining() - elapsed));
            } else {
                snapshot.setBlackTimeRemaining(Math.max(0L, snapshot.getBlackTimeRemaining() - elapsed));
            }
            snapshot.setTurnStartTime(now);
            String updatedPgn = (snapshot.getPgn() == null || snapshot.getPgn().isEmpty())
                    ? moveUci
                    : snapshot.getPgn() + " " + moveUci;
            snapshot.setPgn(updatedPgn);

            if (board.isMated() || board.isDraw() || board.isStaleMate()) {

                Boolean handlesFinalization = redisTemplate.delete(redisKey);

                if (Boolean.TRUE.equals(handlesFinalization)) {

                    ChessMatchModel.WinReason reason = null;
                    if (board.isMated()) {
                        reason = ChessMatchModel.WinReason.CHECKMATE;
                        Long winnerId = board.getSideToMove().toString().equals("WHITE")
                                ? snapshot.getBlackPlayerId()
                                : snapshot.getWhitePlayerId();
                        snapshot.setWinnerId(winnerId);
                    }
                    if (board.isDraw())
                        reason = ChessMatchModel.WinReason.DRAW_MUTUAL;

                    if (board.isStaleMate())
                        reason = ChessMatchModel.WinReason.DRAW_STALEMATE;

                    finalizeMatch(snapshot, reason);
                    return new MatchEndBroadcastDto("END", reason.toString(), snapshot.getWinnerId(), moveUci);
                } else {
                    return null;
                }
            }

            String nextTurnColor = board.getSideToMove().toString();


            snapshot.setFen(board.getFen());
            snapshot.setTurnOwner(board.getSideToMove().toString());
            redisTemplate.opsForValue().set(redisKey, snapshot, 2, TimeUnit.HOURS);
            return new MoveBroadcastDto(
                    "MOVE",
                    moveUci,
                    nextTurnColor,
                    snapshot.getWhiteTimeRemaining(),
                    snapshot.getBlackTimeRemaining()
            );

        } catch (Exception e) {
            return null;
        }
    }


    public void finalizeMatch(MatchSnapshot snapshot, ChessMatchModel.WinReason reason) {
        ChessMatchModel completedMatch = new ChessMatchModel();
        completedMatch.setWhitePlayerId(snapshot.getWhitePlayerId());
        completedMatch.setBlackPlayerId(snapshot.getBlackPlayerId());
        completedMatch.setCreatedAt(LocalDateTime.now());
        completedMatch.setEndedAt(LocalDateTime.now());
        completedMatch.setPgn(snapshot.getPgn());

        completedMatch.setWinnerPlayerId(snapshot.getWinnerId());
        completedMatch.setWinReason(reason);
        chessMatchRepository.save(completedMatch);
    }

    public void handleResign(String matchId, Long playerId) {
        String redisKey = KEY_PREFIX + matchId;
        MatchSnapshot snapshot = (MatchSnapshot) redisTemplate.opsForValue().get(redisKey);
        if (snapshot == null) return;

        Boolean deleted = redisTemplate.delete(redisKey);
        if (!Boolean.TRUE.equals(deleted)) return;

        Long winnerId = snapshot.getWhitePlayerId().equals(playerId)
                ? snapshot.getBlackPlayerId()
                : snapshot.getWhitePlayerId();
        snapshot.setWinnerId(winnerId);
        finalizeMatch(snapshot, ChessMatchModel.WinReason.RESIGNATION);
        messagingTemplate.convertAndSend("/sub/match/" + matchId,
                new MatchEndBroadcastDto("END", "RESIGN", winnerId, null));
    }

    public void handleDraw(String matchId, Long playerId) {
        String offerKey = DRAW_OFFER_PREFIX + matchId;
        Long existingOffer = (Long) redisTemplate.opsForValue().get(offerKey);

        if (existingOffer == null) {
            redisTemplate.opsForValue().set(offerKey, playerId, 2, TimeUnit.MINUTES);
            MatchSnapshot snapshot = (MatchSnapshot) redisTemplate.opsForValue().get(KEY_PREFIX + matchId);
            if (snapshot == null) return;
            messagingTemplate.convertAndSend("/sub/match/" + matchId,
                    new DrawOfferDto("DRAW_OFFER",playerId));
            return;
        }

        if (existingOffer.equals(playerId)) return;
        MatchSnapshot snapshot= (MatchSnapshot) redisTemplate.opsForValue().get(KEY_PREFIX+matchId);
        redisTemplate.delete(offerKey);
        Boolean deleted = redisTemplate.delete(KEY_PREFIX + matchId);
        if (!Boolean.TRUE.equals(deleted)) return;
        if (snapshot == null) return;
        finalizeMatch(snapshot,ChessMatchModel.WinReason.DRAW_MUTUAL);
        messagingTemplate.convertAndSend("/sub/match/" + matchId,
                new MatchEndBroadcastDto("END", "DRAW", null, null));
    }
}