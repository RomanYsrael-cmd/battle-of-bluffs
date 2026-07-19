package com.romanysrael.battleofbluffs.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_reports")
public class PlayerReportEntity {
    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;

    @Column(name = "match_id")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportCategory category;

    @Column(length = 1000)
    private String comment;

    @Column(name = "chat_message_references")
    private String chatMessageReferences;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlayerReportEntity() {
    }

    public PlayerReportEntity(
            UUID id,
            UUID reporterId,
            UUID reportedUserId,
            UUID matchId,
            ReportCategory category,
            String comment,
            String chatMessageReferences,
            Instant createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.matchId = matchId;
        this.category = category;
        this.comment = comment;
        this.chatMessageReferences = chatMessageReferences;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getReportedUserId() {
        return reportedUserId;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public ReportCategory getCategory() {
        return category;
    }

    public String getComment() {
        return comment;
    }

    public String getChatMessageReferences() {
        return chatMessageReferences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
