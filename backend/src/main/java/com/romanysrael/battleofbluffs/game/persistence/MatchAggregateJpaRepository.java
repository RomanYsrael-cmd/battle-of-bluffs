package com.romanysrael.battleofbluffs.game.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAggregateJpaRepository extends JpaRepository<MatchAggregateEntity, UUID> { }
