package com.romanysrael.battleofbluffs.game.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class MatchAggregateEntity {
    @Id
    private UUID id;
    @Column(name = "room_code", unique = true)
    private String roomCode;
    @Column(nullable = false)
    private String mode;
    @Column(name = "timer_mode", nullable = false)
    private String timerMode;
    @Column(nullable = false)
    private String phase;
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "terminal_at")
    private Instant terminalAt;
    @Version
    @Column(name = "persistence_version", nullable = false)
    private long persistenceVersion;

    protected MatchAggregateEntity() { }

    public MatchAggregateEntity(UUID id, String roomCode, String phase, long aggregateVersion,
                                String snapshotJson, Instant now, boolean terminal) {
        this.id = id;
        this.roomCode = roomCode;
        this.mode = "CASUAL";
        this.timerMode = "CASUAL_UNTIMED";
        this.phase = phase;
        this.aggregateVersion = aggregateVersion;
        this.snapshotJson = snapshotJson;
        this.createdAt = now;
        this.updatedAt = now;
        this.terminalAt = terminal ? now : null;
    }

    public void update(String phase, long aggregateVersion, String snapshotJson,
                       Instant now, boolean terminal) {
        this.phase = phase;
        this.aggregateVersion = aggregateVersion;
        this.snapshotJson = snapshotJson;
        this.updatedAt = now;
        if (terminal && terminalAt == null) {
            terminalAt = now;
        }
    }

    public UUID id() { return id; }
    public String roomCode() { return roomCode; }
    public String snapshotJson() { return snapshotJson; }
}
