package com.romanysrael.battleofbluffs.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_chat_messages")
public class MatchChatMessageEntity {
    @Id
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchChatMessageEntity() {
    }

    public MatchChatMessageEntity(
            UUID id,
            UUID matchId,
            UUID senderId,
            long sequenceNumber,
            String body,
            Instant createdAt) {
        this.id = id;
        this.matchId = matchId;
        this.senderId = senderId;
        this.sequenceNumber = sequenceNumber;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
