package com.romanysrael.battleofbluffs.game.application;

import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMatchRepository implements MatchRepository {
    private final ConcurrentHashMap<UUID, PrivateMatch> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byCode = new ConcurrentHashMap<>();

    public void save(PrivateMatch match) {
        byId.put(match.id, match);
        byCode.put(match.roomCode, match.id);
    }

    public Optional<PrivateMatch> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<PrivateMatch> findByRoomCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        UUID id = byCode.get(code.toUpperCase(Locale.ROOT));
        return id == null ? Optional.empty() : findById(id);
    }
}
