package com.chess.repositories;

import com.chess.models.entity.ChessMatchModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChessMatchRepository extends JpaRepository<ChessMatchModel, Long> {
}