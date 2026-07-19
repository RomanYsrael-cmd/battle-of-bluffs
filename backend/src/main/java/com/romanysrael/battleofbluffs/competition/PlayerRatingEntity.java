package com.romanysrael.battleofbluffs.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_ratings")
public class PlayerRatingEntity {
    @EmbeddedId
    private PlayerRatingId id;

    @Column(nullable = false)
    private int rating;

    @Column(name = "rated_games", nullable = false)
    private int ratedGames;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int draws;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerRatingEntity() {
    }

    public PlayerRatingEntity(UUID userId, UUID seasonId, Instant now) {
        this.id = new PlayerRatingId(userId, seasonId);
        this.rating = 1200;
        this.updatedAt = now;
    }

    public PlayerRatingId getId() {
        return id;
    }

    public int getRating() {
        return rating;
    }

    public int getRatedGames() {
        return ratedGames;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void apply(int delta, double actualScore, Instant now) {
        rating += delta;
        ratedGames++;
        if (actualScore == 1.0) {
            wins++;
        } else if (actualScore == 0.0) {
            losses++;
        } else {
            draws++;
        }
        updatedAt = now;
    }

    @Embeddable
    public record PlayerRatingId(
            @Column(name = "user_id") UUID userId,
            @Column(name = "season_id") UUID seasonId) implements Serializable {
    }
}
