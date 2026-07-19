package com.romanysrael.battleofbluffs.game.application;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepository {
    void save(PrivateMatch match);
    Optional<PrivateMatch> findById(UUID id);
    Optional<PrivateMatch> findByRoomCode(String roomCode);
}
