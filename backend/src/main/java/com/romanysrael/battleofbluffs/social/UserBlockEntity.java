package com.romanysrael.battleofbluffs.social;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_blocks")
public class UserBlockEntity {
    @EmbeddedId
    private UserBlockId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserBlockEntity() {
    }

    public UserBlockEntity(UUID blockerId, UUID blockedId, Instant createdAt) {
        this.id = new UserBlockId(blockerId, blockedId);
        this.createdAt = createdAt;
    }

    public UserBlockId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public record UserBlockId(
            @Column(name = "blocker_id") UUID blockerId,
            @Column(name = "blocked_id") UUID blockedId) implements Serializable {
    }
}
