    package com.chess.services.matchmaking;

    import com.chess.models.dto.MatchSnapshot;
    import com.chess.models.entity.PlayerModel;
    import com.chess.services.auth.SecurityService;
    import com.github.bhlangonijr.chesslib.game.Player;
    import lombok.Data;
    import lombok.NoArgsConstructor;
    import lombok.RequiredArgsConstructor;
    import org.springframework.data.redis.core.RedisTemplate;
    import org.springframework.scheduling.annotation.Scheduled;
    import org.springframework.stereotype.Service;

    import java.util.*;
    import java.util.concurrent.ConcurrentLinkedQueue;
    import java.util.concurrent.TimeUnit;

    @Service
    @RequiredArgsConstructor
    public class MatchmakingService {

        private final RedisTemplate<String, Object> redisTemplate;
        private final SecurityService securityService;

        private final ConcurrentLinkedQueue<Long> waitingQueue = new ConcurrentLinkedQueue<>();
        private static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        private static final String MATCH_PREFIX = "match:";
        private static final int Number_of_Buckets = 10;
        private final Long[] buckets = new Long[Number_of_Buckets];
        {
            Arrays.fill(buckets,-1L);
        }
        private final HashMap<Long, PlayerEntry> players=new HashMap<>();
        @Data
        @NoArgsConstructor
        class PlayerEntry {
            Long playerId;
            int originalBucket;
            long joinTime;
        }

        public synchronized MatchSnapshot joinQueue(PlayerModel player) {
            int bucketIdx=FindBucket(player);
            if(bucketIdx==-1){
                return null;
            }
            if(buckets[bucketIdx]==-1){
                buckets[bucketIdx]=player.getId();
                PlayerEntry playerEntry = new PlayerEntry();
                playerEntry.setPlayerId(player.getId());
                playerEntry.setOriginalBucket(bucketIdx);
                playerEntry.setJoinTime(System.currentTimeMillis());
                players.put(player.getId(), playerEntry);
            }
            else{
                Long opponentId=buckets[bucketIdx];
                leaveQueue(opponentId);
                if (Math.random() < 0.5) {
                    return createLiveMatch(opponentId, player.getId());
                } else {
                    return createLiveMatch(player.getId(), opponentId);
                }
            }
            return null;
        }

        public synchronized void leaveQueue(Long playerId) {
            PlayerEntry playerEntry=players.get(playerId);
            players.remove(playerId);
            buckets[playerEntry.getOriginalBucket()]=-1L;
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
        private int FindBucket(PlayerModel player) {
            int elo = Math.clamp(player.getElo(), 0, 1000);
            return elo / 100;
        }

        @Scheduled(fixedRate = 5000)
        public synchronized void sweepQueue() {
            long now = System.currentTimeMillis();

            for (PlayerEntry entry : new ArrayList<>(players.values())) {
                long waited = now - entry.getJoinTime();

                if (waited > 20_000) {
                    leaveQueue(entry.getPlayerId());
                } else if (waited > 5_000) {
                    expandSearch(entry);
                }
            }
        }

        private void expandSearch(PlayerEntry entry) {
            int[] neighbours = {entry.getOriginalBucket() - 1, entry.getOriginalBucket() + 1};

            for (int neighbour : neighbours) {
                if (neighbour < 0 || neighbour >= Number_of_Buckets) continue;
                if (buckets[neighbour] == -1L) continue;

                Long opponentId = buckets[neighbour];
                leaveQueue(entry.getPlayerId());
                leaveQueue(opponentId);

                if (Math.random() < 0.5) {
                    createLiveMatch(entry.getPlayerId(), opponentId);
                } else {
                    createLiveMatch(opponentId, entry.getPlayerId());
                }
                return;
            }
        }
    }