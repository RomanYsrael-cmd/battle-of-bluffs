package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.*;
import java.util.*;

final class PrivateMatch {
    final UUID id;
    final String roomCode;
    final MatchMode mode;
    final TimerMode timerMode;
    final EnumMap<PlayerSide, String> players = new EnumMap<>(PlayerSide.class);
    final EnumMap<PlayerSide, List<Piece>> formations = new EnumMap<>(PlayerSide.class);
    final EnumSet<PlayerSide> locked = EnumSet.noneOf(PlayerSide.class);
    final List<PublicMatchEvent> events = new ArrayList<>();
    final LinkedHashMap<UUID, StoredCommand> commands = new LinkedHashMap<>();
    final Map<String, Integer> repetitions = new HashMap<>();
    final Map<UUID, UUID> publicPieceIds = new HashMap<>();
    long version = 1;
    MatchState state;
    long nextEventSequence = 1;

    PrivateMatch(UUID id, String roomCode, String creator) {
        this(id, roomCode, creator, MatchMode.CASUAL, TimerMode.CASUAL_UNTIMED);
    }

    PrivateMatch(
            UUID id,
            String roomCode,
            String creator,
            MatchMode mode,
            TimerMode timerMode) {
        this.id = id;
        this.roomCode = roomCode;
        this.mode = mode;
        this.timerMode = timerMode;
        players.put(PlayerSide.PLAYER_ONE, creator);
    }

    PlayerSide sideOf(String playerId) {
        return players.entrySet().stream()
                .filter(entry -> entry.getValue().equals(playerId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    record CommandFingerprint(String operation, List<String> components) {
        CommandFingerprint {
            components = List.copyOf(components);
        }
    }

    record StoredCommand(CommandFingerprint fingerprint, MatchCommandResult result) { }
}
