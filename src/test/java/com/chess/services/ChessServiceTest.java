package com.chess.services;


import com.chess.api.websocket.dto.MoveBroadcastDto;
import com.chess.models.dto.MatchSnapshot;
import com.chess.models.entity.ChessMatchModel;
import com.chess.repositories.ChessMatchRepository;
import com.chess.services.LiveGame.ChessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChessServiceTest {

    @InjectMocks
    private ChessService chessService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChessMatchRepository chessMatchRepository;


    @Test
    void ShouldSaveAndWipeFromRedisWhenMatchIsFinalized(){
        String foolsMateSetupFen = "rnbqkbnr/ppppp1pp/8/5p2/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2";
        long eachplayertimeinmilliseconds =600_000L;
        MatchSnapshot snapshot = new MatchSnapshot("123", "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5Q2/PPPP1PPP/RNB1KBNR w KQkq - 2 3", "WHITE", 11L, 99L, eachplayertimeinmilliseconds,eachplayertimeinmilliseconds, System.currentTimeMillis(),"e2e4 e7e5 d2d4 Nc6 d4e5");

        snapshot.setFen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1KBNR w KQkq - 4 4");

        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("match:123")).thenReturn(snapshot);
        when(redisTemplate.delete("match:123")).thenReturn(true);
        var result = chessService.handlePlayerMove("123", 11L, "h5f7");

        verify(chessMatchRepository, times(1)).save(any(ChessMatchModel.class));
        verify(redisTemplate, times(1)).delete("match:123");
    }
    @Test
    void shouldRejectMove_WhenMoveIsIllegal() {

        MatchSnapshot snapshot = new MatchSnapshot("123", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "WHITE", 11L, 99L,100000000,100000000,100000000,"");
        
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("match:123")).thenReturn(snapshot);

        var result = chessService.handlePlayerMove("123", 11L, "e2e5");

        assertNull(result);
    }
}
