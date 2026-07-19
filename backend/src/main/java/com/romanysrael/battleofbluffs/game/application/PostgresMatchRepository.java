package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.Piece;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateEntity;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateJpaRepository;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Repository
public class PostgresMatchRepository implements MatchRepository {
    private final MatchAggregateJpaRepository aggregates;
    private final MatchSnapshotCodec codec;
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<UUID, PrivateMatch> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byCode = new ConcurrentHashMap<>();

    public PostgresMatchRepository(
            MatchAggregateJpaRepository aggregates,
            MatchSnapshotCodec codec,
            JdbcTemplate jdbc) {
        this.aggregates = aggregates;
        this.codec = codec;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void restorePersistedMatches() {
        aggregates.findAll().forEach(entity -> cache(codec.decode(entity.snapshotJson())));
    }

    @Override
    @Transactional
    public void save(PrivateMatch match) {
        Instant now = Instant.now();
        String snapshot = codec.encode(match);
        MatchPhase phase = match.state == null ? MatchPhase.FORMATION : match.state.phase();
        MatchAggregateEntity entity = aggregates.findById(match.id)
                .orElseGet(() -> new MatchAggregateEntity(
                        match.id,
                        match.roomCode,
                        match.mode.name(),
                        match.timerMode.name(),
                        phase.name(),
                        match.version,
                        snapshot,
                        now,
                        phase == MatchPhase.TERMINAL));
        entity.update(
                match.mode.name(),
                match.timerMode.name(),
                phase.name(),
                match.version,
                snapshot,
                now,
                phase == MatchPhase.TERMINAL);
        aggregates.saveAndFlush(entity);

        persistPlayers(match, now);
        persistFormations(match);
        persistEvents(match, now);
        persistCommands(match, now);
        jdbc.update("""
                INSERT INTO match_snapshots(match_id, aggregate_version, snapshot_json, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (match_id, aggregate_version) DO NOTHING
                """, match.id, match.version, snapshot, Timestamp.from(now));
        cache(match);
    }

    @Override
    public Optional<PrivateMatch> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<PrivateMatch> findByRoomCode(String roomCode) {
        if (roomCode == null) {
            return Optional.empty();
        }
        UUID id = byCode.get(roomCode.toUpperCase(Locale.ROOT));
        return id == null ? Optional.empty() : findById(id);
    }

    @Override
    public Collection<PrivateMatch> findAll() {
        return java.util.List.copyOf(byId.values());
    }

    private void persistPlayers(PrivateMatch match, Instant now) {
        jdbc.update("DELETE FROM match_players WHERE match_id = ?", match.id);
        match.players.forEach((side, playerKey) -> {
            UUID accountId = uuidOrNull(playerKey);
            jdbc.update("""
                    INSERT INTO match_players(match_id, side, user_id, player_key, joined_at)
                    VALUES (?, ?, (SELECT id FROM users WHERE id = ?), ?, ?)
                    """, match.id, side.name(), accountId, playerKey, Timestamp.from(now));
        });
    }

    private void persistFormations(PrivateMatch match) {
        jdbc.update("DELETE FROM match_formations WHERE match_id = ?", match.id);
        match.formations.forEach((side, pieces) -> pieces.forEach(piece ->
                persistFormationPiece(match, side, piece)));
    }

    private void persistFormationPiece(PrivateMatch match, PlayerSide side, Piece piece) {
        Integer row = piece.position().map(position -> position.row()).orElse(null);
        Integer column = piece.position().map(position -> position.column()).orElse(null);
        jdbc.update("""
                INSERT INTO match_formations(
                    match_id, side, piece_id, public_piece_id, rank,
                    row_number, column_number, alive, locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                match.id,
                side.name(),
                piece.id(),
                match.publicPieceIds.get(piece.id()),
                piece.rank().name(),
                row,
                column,
                piece.isAlive(),
                match.locked.contains(side));
    }

    private void persistEvents(PrivateMatch match, Instant now) {
        for (PublicMatchEvent event : match.events) {
            jdbc.update("""
                    INSERT INTO match_events(
                        match_id, sequence_number, event_type, event_json, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (match_id, sequence_number) DO NOTHING
                    """,
                    match.id,
                    event.sequence(),
                    event.type().name(),
                    codec.encodeEvent(event),
                    Timestamp.from(now));
        }
    }

    private void persistCommands(PrivateMatch match, Instant now) {
        for (Map.Entry<UUID, PrivateMatch.StoredCommand> entry : match.commands.entrySet()) {
            PrivateMatch.StoredCommand command = entry.getValue();
            String fingerprint = command.fingerprint().operation()
                    + "\u001f" + String.join("\u001f", command.fingerprint().components());
            jdbc.update("""
                    INSERT INTO match_commands(
                        match_id, command_id, fingerprint, result_json, accepted_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (match_id, command_id) DO NOTHING
                    """,
                    match.id,
                    entry.getKey(),
                    fingerprint,
                    codec.encodeResult(command.result()),
                    Timestamp.from(now));
        }
    }

    private void cache(PrivateMatch match) {
        byId.put(match.id, match);
        if (match.roomCode != null) {
            byCode.put(match.roomCode.toUpperCase(Locale.ROOT), match.id);
        }
    }

    private static UUID uuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
