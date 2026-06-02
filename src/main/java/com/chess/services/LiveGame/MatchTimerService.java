package com.chess.services.LiveGame;

import com.chess.api.websocket.dto.MatchEndBroadcastDto;
import com.chess.models.dto.MatchSnapshot;
import com.chess.models.entity.ChessMatchModel;
import com.chess.models.entity.ChessMatchModel.WinReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MatchTimerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChessService chessService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String MATCH_PREFIX = "match:";

    // Cleaned up naming convention & visibility
    public static final long TEN_MINUTES = 600_000L;

    // Tracks the last timestamp System.currentTimeMillis() a player was seen
    private final ConcurrentHashMap<Long, Long> lastPing = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 10000) // Runs every 10 seconds
    public void checkMatchTimer() {
        // NOTE: For a massive scale production system, maintain a Redis Set of active matchIds
        // to avoid calling .keys() entirely.
        Set<String> keys = redisTemplate.keys(MATCH_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        long now = System.currentTimeMillis();

        keys.stream()
                .map(key -> (MatchSnapshot) redisTemplate.opsForValue().get(key))
                .filter(Objects::nonNull)
                .forEach(match -> {
                    WinReason reason = getTerminationReason(match, now);
                    if (reason == null) return;

                    Boolean deleted = redisTemplate.delete(MATCH_PREFIX + match.getMatchId());
                    if (!Boolean.TRUE.equals(deleted)) return;

                    applyTermination(match, reason);
                    chessService.finalizeMatch(match, reason);
                });

        // Clean up memory: if a player hasn't been seen for over a minute, wipe them.
        lastPing.entrySet().removeIf(entry -> now - entry.getValue() > 60_000);
    }

    private WinReason getTerminationReason(MatchSnapshot snapshot, long now) {
        // 1. Check if they ran completely out of turn time (60 seconds)
        if (now - snapshot.getTurnStartTime() > 60_000) {
            return WinReason.TIMEOUT;
        }

        // 2. Check White Abandonment (No ping for 30s OR never pinged since game started)
        Long whitePing = lastPing.get(snapshot.getWhitePlayerId());
        if (isAbandoned(whitePing, snapshot.getTurnStartTime(), now)) {
            return WinReason.ABANDON_WHITE;
        }

        // 3. Check Black Abandonment
        Long blackPing = lastPing.get(snapshot.getBlackPlayerId());
        if (isAbandoned(blackPing, snapshot.getTurnStartTime(), now)) {
            return WinReason.ABANDON_BLACK;
        }

        return null;
    }

    private boolean isAbandoned(Long lastPingTime, long gameStartTime, long now) {
        if (lastPingTime != null) {
            // Player has pinged before, check if they went silent for 30 seconds
            return (now - lastPingTime) > 30_000;
        } else {
            // Player NEVER pinged. Check if the game has been active for more than 30s
            return (now - gameStartTime) > 30_000;
        }
    }

    private void applyTermination(MatchSnapshot match, WinReason reason) {
        Long winnerId = null;
        switch (reason) {
            case TIMEOUT -> {
                if ("WHITE".equals(match.getTurnOwner())) {
                    match.setWhiteTimeRemaining(0);
                    winnerId = match.getBlackPlayerId();
                } else {
                    match.setBlackTimeRemaining(0);
                    winnerId = match.getWhitePlayerId();
                }
            }
            case ABANDON_WHITE -> {
                winnerId = match.getBlackPlayerId();
                match.setWhiteTimeRemaining(-1);
            }
            case ABANDON_BLACK -> {
                winnerId = match.getWhitePlayerId();
                match.setBlackTimeRemaining(-1);
            }
        }
        match.setWinnerId(winnerId);
        MatchEndBroadcastDto end = new MatchEndBroadcastDto("END", reason.toString(), winnerId);
        messagingTemplate.convertAndSend("/sub/match/" + match.getMatchId(), end);
    }

    public void ping(Long playerId) {
        lastPing.put(playerId, System.currentTimeMillis());
    }
}
