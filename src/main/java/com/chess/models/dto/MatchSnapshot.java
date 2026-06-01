package com.chess.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchSnapshot implements Serializable {

    private String matchId;
    private String fen;
    private String turnOwner;
    private Long whitePlayerId;
    private Long blackPlayerId;
    private long whiteTimeRemaining;
    private long blackTimeRemaining;
    private long turnStartTime;
    private Long winnerId;
    private String pgn;


}