package com.chess.models.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chess_matches")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChessMatchModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long whitePlayerId;
    private Long blackPlayerId;


    private Long winnerPlayerId;

    @Enumerated(EnumType.STRING)
    private WinReason winReason;

    @Column(length = 2000)
    private String pgn;

    private LocalDateTime createdAt;
    private LocalDateTime endedAt;

    public enum WinReason { CHECKMATE, RESIGNATION, TIMEOUT, DRAW_MUTUAL, DRAW_STALEMATE,ABANDON_WHITE,ABANDON_BLACK }

}