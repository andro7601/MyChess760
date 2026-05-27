package com.chess.services.matchmaking;

import com.chess.models.dto.MatchSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentLinkedQueue<Long> waitingQueue = new ConcurrentLinkedQueue<>();
    private static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String MATCH_PREFIX = "match:";

    public synchronized MatchSnapshot joinQueue(Long playerId) {
        if (waitingQueue.contains(playerId)) {
            return null;
        }

        if (waitingQueue.isEmpty()) {
            waitingQueue.add(playerId);
            return null;
        }

        Long opponentId = waitingQueue.peek();

        waitingQueue.poll();

        return createLiveMatch(opponentId, playerId);
    }

    public synchronized void leaveQueue(Long playerId) {
        
        waitingQueue.remove(playerId);
    }

    private MatchSnapshot createLiveMatch(Long whitePlayerId, Long blackPlayerId) {
        String matchUuid = UUID.randomUUID().toString();
        MatchSnapshot snapshot = new MatchSnapshot();
        snapshot.setMatchId(matchUuid);
        snapshot.setFen(STARTING_FEN);
        snapshot.setTurnOwner("WHITE");
        snapshot.setWhitePlayerId(whitePlayerId);
        snapshot.setBlackPlayerId(blackPlayerId);
        snapshot.setPgn("");
        redisTemplate.opsForValue().set(MATCH_PREFIX + matchUuid, snapshot, 2, TimeUnit.HOURS);

        return snapshot;
    }
    public MatchSnapshot Reconnect(Long playerId) {
        Set<String> keys = redisTemplate.keys(MATCH_PREFIX + "*");
        if (keys.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            MatchSnapshot snapshot = (MatchSnapshot) redisTemplate.opsForValue().get(key);
            if (snapshot != null) {
                if (playerId.equals(snapshot.getWhitePlayerId()) || playerId.equals(snapshot.getBlackPlayerId())) {
                    return snapshot;
                }
            }
        }
        return null;
    }
}