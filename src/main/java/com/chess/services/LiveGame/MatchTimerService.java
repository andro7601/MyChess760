    package com.chess.services.LiveGame;

    import com.chess.api.websocket.dto.MatchEndBroadcastDto;
    import com.chess.models.dto.MatchSnapshot;
    import com.github.bhlangonijr.chesslib.Board;
    import lombok.RequiredArgsConstructor;
    import org.springframework.data.redis.core.RedisTemplate;
    import org.springframework.messaging.core.AbstractDestinationResolvingMessagingTemplate;
    import org.springframework.messaging.handler.annotation.MessageMapping;
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
        private final ConcurrentHashMap<Long,Long> lastPing = new ConcurrentHashMap<>();


        @Scheduled(fixedRate = 10000)
        public void checkMatchTimer() {
            Set<String> keys = redisTemplate.keys(MATCH_PREFIX + "*");
            if (keys == null || keys.isEmpty()) return;

            long now = System.currentTimeMillis();

            keys.stream()
                    .map(key -> (MatchSnapshot) redisTemplate.opsForValue().get(key))
                    .filter(Objects::nonNull)
                    .forEach(match -> {
                        String reason = getTerminationReason(match, now);
                        if (reason == null) return;

                        Boolean deleted = redisTemplate.delete(MATCH_PREFIX + match.getMatchId());
                        if (!Boolean.TRUE.equals(deleted)) return;

                        applyTermination(match, reason);
                        chessService.finalizeMatch(match,reason);
                    });
            lastPing.entrySet().removeIf(entry -> now - entry.getValue() > 60_000);
        }

        private String getTerminationReason(MatchSnapshot match, long now) {
            if (now - match.getTurnStartTime() > 60_000) return "TIMEOUT";

            Long whitePing = lastPing.get(match.getWhitePlayerId());
            if (whitePing != null && now - whitePing > 30_000) return "ABANDON_WHITE";

            Long blackPing = lastPing.get(match.getBlackPlayerId());
            if (blackPing != null && now - blackPing > 30_000) return "ABANDON_BLACK";

            return null;
        }

        private void applyTermination(MatchSnapshot match, String reason) {
            Long winnerId=null;
            switch (reason) {
                case "TIMEOUT" -> {
                    if (match.getTurnOwner().equals("WHITE")) {
                        match.setWhiteTimeRemaining(0);
                        winnerId=match.getBlackPlayerId();
                    }
                    else {
                        match.setBlackTimeRemaining(0);
                        winnerId=match.getWhitePlayerId();
                    }
                }
                case "ABANDON_WHITE" -> {
                    winnerId=match.getBlackPlayerId();
                    match.setWhiteTimeRemaining(-1);
                }
                case "ABANDON_BLACK" -> {
                    winnerId=match.getWhitePlayerId();
                    match.setBlackTimeRemaining(-1);
                }
            }
            MatchEndBroadcastDto end = new MatchEndBroadcastDto("END", reason, winnerId);
            messagingTemplate.convertAndSend("/sub/match/" + match.getMatchId(), end);
        }

        public void ping(Long playerId){
            lastPing.put(playerId,System.currentTimeMillis());
        }
    }
