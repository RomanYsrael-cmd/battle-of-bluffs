package com.romanysrael.battleofbluffs.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rating_changes")
public class RatingChangeEntity {
    @Id
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "pre_match_rating", nullable = false)
    private int preMatchRating;

    @Column(name = "expected_score", nullable = false, precision = 8, scale = 6)
    private BigDecimal expectedScore;

    @Column(name = "actual_score", nullable = false, precision = 2, scale = 1)
    private BigDecimal actualScore;

    @Column(name = "rating_delta", nullable = false)
    private int ratingDelta;

    @Column(name = "post_match_rating", nullable = false)
    private int postMatchRating;

    @Column(name = "k_factor", nullable = false)
    private int kFactor;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected RatingChangeEntity() {
    }

    public RatingChangeEntity(
            UUID id,
            UUID matchId,
            UUID userId,
            UUID seasonId,
            int preMatchRating,
            BigDecimal expectedScore,
            BigDecimal actualScore,
            int ratingDelta,
            int postMatchRating,
            int kFactor,
            Instant calculatedAt) {
        this.id = id;
        this.matchId = matchId;
        this.userId = userId;
        this.seasonId = seasonId;
        this.preMatchRating = preMatchRating;
        this.expectedScore = expectedScore;
        this.actualScore = actualScore;
        this.ratingDelta = ratingDelta;
        this.postMatchRating = postMatchRating;
        this.kFactor = kFactor;
        this.calculatedAt = calculatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSeasonId() {
        return seasonId;
    }

    public int getPreMatchRating() {
        return preMatchRating;
    }

    public BigDecimal getExpectedScore() {
        return expectedScore;
    }

    public BigDecimal getActualScore() {
        return actualScore;
    }

    public int getRatingDelta() {
        return ratingDelta;
    }

    public int getPostMatchRating() {
        return postMatchRating;
    }

    public int getKFactor() {
        return kFactor;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }
}
