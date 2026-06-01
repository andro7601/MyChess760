package com.chess.controllers;

import com.chess.api.websocket.dto.MoveBroadcastDto;
import com.chess.api.websocket.dto.MoveRequestDto;
import com.chess.models.dto.MatchSnapshot;
import com.chess.models.entity.PlayerModel;
import com.chess.repositories.ChessMatchRepository;
import com.chess.repositories.PlayerRepository;
import com.chess.services.auth.SecurityService;
import com.chess.services.auth.jwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
)//spring trys to build infrastructure to postgres flyway to postgres then redis and jpa hibernate to postgres
//but when starting ./mvnw test we dont actually set them up and connection becomes null and causes a crash
public class WebSocketIntegrationTest {

    @MockitoBean
    private SecurityService securityService;

    @LocalServerPort
    private int port;

    @MockitoBean
    private PlayerRepository playerRepository;

    @MockitoBean
    private ChessMatchRepository chessMatchRepository;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private ValueOperations<String, Object> valueOperations;

    @Autowired
    private jwtService jwtService;

    private String serverWsUrl;
    private WebSocketStompClient testClient;

    @BeforeEach
    void setup() {
        this.serverWsUrl = "ws://localhost:" + port + "/ws/websocket";
        this.testClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.testClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void verifyMatchmakingFlowAndRouting() throws Exception {
        Map<String, Object> redisStore = new ConcurrentHashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisStore.get(invocation.getArgument(0)));

        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        PlayerModel playerModel1 = new PlayerModel();
        playerModel1.setId(1L);
        playerModel1.setUsername("player1");
        playerModel1.setPassword("password1");
        playerModel1.setElo(150);

        PlayerModel playerModel2 = new PlayerModel();
        playerModel2.setId(2L);
        playerModel2.setUsername("player2");
        playerModel2.setPassword("password2");
        playerModel2.setElo(150);

        when(securityService.getPlayer())
                .thenReturn(playerModel1)  // first call
                .thenReturn(playerModel2); // second call
        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerModel1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(playerModel2));
        when(playerRepository.findByUsername("player1")).thenReturn(Optional.of(playerModel1));
        when(playerRepository.findByUsername("player2")).thenReturn(Optional.of(playerModel2));
        when(playerRepository.save(ArgumentMatchers.any(PlayerModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String token1 = jwtService.generateToken(playerModel1);

        WebSocketHttpHeaders httpHeaders1 = new WebSocketHttpHeaders();
        httpHeaders1.add("Authorization", "Bearer " + token1);

        StompHeaders stompHeaders1 = new StompHeaders();
        stompHeaders1.add("Authorization", "Bearer " + token1);

        StompSession session1 = testClient.connectAsync(
                serverWsUrl, httpHeaders1, stompHeaders1, new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        String token2 = jwtService.generateToken(playerModel2);

        WebSocketHttpHeaders httpHeaders2 = new WebSocketHttpHeaders();
        httpHeaders2.add("Authorization", "Bearer " + token2);

        StompHeaders stompHeaders2 = new StompHeaders();
        stompHeaders2.add("Authorization", "Bearer " + token2);

        StompSession session2 = testClient.connectAsync(
                serverWsUrl, httpHeaders2, stompHeaders2, new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        CompletableFuture<MatchSnapshot> player1Mailbox = new CompletableFuture<>();
        CompletableFuture<MatchSnapshot> player2Mailbox = new CompletableFuture<>();

        session1.subscribe("/user/sub/queue", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return MatchSnapshot.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                player1Mailbox.complete((MatchSnapshot) payload);
            }
        });

        session2.subscribe("/user/sub/queue", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return MatchSnapshot.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                player2Mailbox.complete((MatchSnapshot) payload);
            }
        });

        session1.send("/app/matchmaking/join", null);
        session2.send("/app/matchmaking/join", null);

        MatchSnapshot matchForPlayer1 = player1Mailbox.get(5, TimeUnit.SECONDS);
        MatchSnapshot matchForPlayer2 = player2Mailbox.get(5, TimeUnit.SECONDS);

        assertNotNull(matchForPlayer1);
        assertNotNull(matchForPlayer2);
        assertEquals(matchForPlayer1.getMatchId(), matchForPlayer2.getMatchId());

        CompletableFuture<MoveBroadcastDto> gameUpdatesMailbox = new CompletableFuture<>();

        session2.subscribe("/sub/match/" + matchForPlayer1.getMatchId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return MoveBroadcastDto.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                gameUpdatesMailbox.complete( (MoveBroadcastDto)  payload);
            }
        });

        String matchId = matchForPlayer1.getMatchId();
        MoveRequestDto move = new MoveRequestDto("e2e4");

        if (matchForPlayer1.getWhitePlayerId().equals(playerModel1.getId())) {
            session1.send("/app/match/" + matchId + "/move", move);
        } else {
            session2.send("/app/match/" + matchId + "/move", move);
        }

        MoveBroadcastDto updatedmatch = gameUpdatesMailbox.get(5, TimeUnit.SECONDS);
        assertNotNull(updatedmatch);
        assertEquals("e2e4", updatedmatch.move());

    }

    @Test
    void verifyLegalMoveGetsNotifyed() throws Exception {

    }
}
