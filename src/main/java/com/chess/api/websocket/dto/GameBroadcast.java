package com.chess.api.websocket.dto;

import com.chess.api.websocket.dto.MoveRequestDto;

public sealed interface GameBroadcast permits MoveBroadcastDto, MatchEndBroadcastDto {}
