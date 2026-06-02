package com.chess.controllers;

import com.chess.api.websocket.dto.DrawOfferDto;
import com.chess.api.websocket.dto.MatchEndBroadcastDto;
import com.chess.api.websocket.dto.MoveBroadcastDto;
import com.chess.api.websocket.dto.MoveRequestDto;
import com.chess.models.dto.MatchSnapshot;
import com.chess.models.entity.PlayerModel;
import com.chess.repositories.ChessMatchRepository;
import com.chess.repositories.PlayerRepository;
import com.chess.services.auth.SecurityService;
import com.chess.services.auth.jwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
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

    @LocalServerPort
    private int port;

    private static StompSession session1;
    private static StompSession session2;
    private static MatchSnapshot sharedMatch;

    private static PlayerModel playerModel1;
    private static PlayerModel playerModel2;

    static {
        playerModel1 = new PlayerModel();
        playerModel1.setId(1L);
        playerModel1.setUsername("player1");
        playerModel1.setPassword("password1");
        playerModel1.setElo(150);

        playerModel2 = new PlayerModel();
        playerModel2.setId(2L);
        playerModel2.setUsername("player2");
        playerModel2.setPassword("password2");
        playerModel2.setElo(150);
    }

    private static Map<String, Object> redisStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setup() throws Exception {
        stubMocks();

        if (session1 != null && session1.isConnected()) return;
        createConnections();
    }

    private void stubMocks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);

            if (redisStore.containsKey(key)) {
                return redisStore.get(key);
            }

            if (key.contains("DRAW") || key.contains("draw")) {
                return null;
            }

            if (sharedMatch != null && key.contains(sharedMatch.getMatchId())) {
                return sharedMatch;
            }

            return null;
        });

        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        when(redisTemplate.delete(anyString())).thenReturn(true);




        when(securityService.getPlayer(any())).thenAnswer(inv -> {
            java.security.Principal principal = inv.getArgument(0, java.security.Principal.class);
            if (principal != null && "player2".equals(principal.getName())) {
                return playerModel2;
            }
            return playerModel1;
        });

        when(securityService.getPlayerId(any())).thenAnswer(inv -> {
            java.security.Principal principal = inv.getArgument(0, java.security.Principal.class);
            if (principal != null && "player2".equals(principal.getName())) {
                return playerModel2.getId();
            }
            return playerModel1.getId();
        });


        when(playerRepository.findById(1L)).thenReturn(Optional.of(playerModel1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(playerModel2));
        when(playerRepository.findByUsername("player1")).thenReturn(Optional.of(playerModel1));
        when(playerRepository.findByUsername("player2")).thenReturn(Optional.of(playerModel2));
        when(playerRepository.save(any(PlayerModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void createConnections() throws Exception {

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        String url = "ws://localhost:" + port + "/ws/websocket";

        session1 = connect(client, url, jwtService.generateToken(playerModel1));
        session2 = connect(client, url, jwtService.generateToken(playerModel2));
    }

    private StompSession connect(WebSocketStompClient client, String url, String token)
            throws Exception {
        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.add("Authorization", "Bearer " + token);
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + token);
        return client.connectAsync(url, httpHeaders, stompHeaders,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    @Test
    @Order(1)
    void verifyMatchmakingFlowAndRouting() throws Exception {
        CompletableFuture<MatchSnapshot> p1Box = new CompletableFuture<>();
        CompletableFuture<MatchSnapshot> p2Box = new CompletableFuture<>();

        session1.subscribe("/user/sub/queue", handler(MatchSnapshot.class, p1Box));
        session2.subscribe("/user/sub/queue", handler(MatchSnapshot.class, p2Box));

        session1.send("/app/matchmaking/join", null);
        session2.send("/app/matchmaking/join", null);

        sharedMatch = p1Box.get(5, TimeUnit.SECONDS);
        MatchSnapshot matchForPlayer2 = p2Box.get(5, TimeUnit.SECONDS);

        assertNotNull(sharedMatch);
        assertNotNull(matchForPlayer2);
        assertEquals(sharedMatch.getMatchId(), matchForPlayer2.getMatchId());
    }

    @Test
    @Order(2)
    void verifyLegalMoveGetsNotified() throws Exception {
        assertNotNull(sharedMatch, "Run matchmaking test first");

        CompletableFuture<MoveBroadcastDto> moveBox = new CompletableFuture<>();
        session2.subscribe("/sub/match/" + sharedMatch.getMatchId(),
                handler(MoveBroadcastDto.class, moveBox));

        MoveRequestDto move = new MoveRequestDto("e2e4");
        StompSession whiteSession = sharedMatch.getWhitePlayerId()
                .equals(playerModel1.getId()) ? session1 : session2;

        whiteSession.send("/app/match/" + sharedMatch.getMatchId() + "/move", move);

        MoveBroadcastDto result = moveBox.get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals("e2e4", result.move());
    }

    @Test
    @Order(3)
    void verifyDrawOfferIsNotified() throws Exception {
        CompletableFuture<DrawOfferDto> p2Box = new CompletableFuture<>();
        CompletableFuture<java.util.Map> endBox = new CompletableFuture<>();

        session2.subscribe("/sub/match/" + sharedMatch.getMatchId(),
                handler(DrawOfferDto.class, p2Box));

        session1.subscribe("/sub/match/" + sharedMatch.getMatchId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return java.util.Map.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                java.util.Map map = (java.util.Map) payload;
                if ("END".equals(map.get("type"))) {
                    endBox.complete(map);
                }
            }
        });

        session1.send("/app/match/" + sharedMatch.getMatchId() + "/draw", null);

        DrawOfferDto drawOfferdto = p2Box.get(5, TimeUnit.SECONDS);
        assertNotNull(drawOfferdto);

        session2.send("/app/match/" + sharedMatch.getMatchId() + "/draw", null);

        java.util.Map endMsg = endBox.get(5, TimeUnit.SECONDS);
        assertNotNull(endMsg);
        assertEquals("DRAW", endMsg.get("reason"));

        verify(redisTemplate, times(1)).delete("match:" + sharedMatch.getMatchId());
        verify(chessMatchRepository, times(1)).save(any());
    }
    @Test
    @Order(4)
    void verifyResignOfferIsNotified() throws Exception {
        CompletableFuture<MatchSnapshot> p1Box = new CompletableFuture<>();
        CompletableFuture<java.util.Map> endbox = new CompletableFuture<>();

        session1.subscribe("/user/sub/queue", handler(MatchSnapshot.class, p1Box));

        session1.send("/app/matchmaking/join", null);
        session2.send("/app/matchmaking/join", null);

        sharedMatch = p1Box.get(5, TimeUnit.SECONDS);

        session2.subscribe("/sub/match/" + sharedMatch.getMatchId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return java.util.Map.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                java.util.Map map = (java.util.Map) payload;
                if ("END".equals(map.get("type"))) {
                    endbox.complete(map);
                }
            }
        });
        session1.send("/app/match/" + sharedMatch.getMatchId() + "/resign", null);

        endbox.get(5, TimeUnit.SECONDS);
        assertNotNull(endbox);
        verify(redisTemplate, times(1)).delete("match:" + sharedMatch.getMatchId());
        verify(chessMatchRepository, times(1)).save(any());
    }

    private <T> StompFrameHandler handler(Class<T> type, CompletableFuture<T> future) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return type; }
            @Override @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders h, Object p) { future.complete((T) p); }
        };
    }
}
