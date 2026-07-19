package com.romanysrael.battleofbluffs.competition;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.MatchSummary;
import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingService {
    public static final int INITIAL_RATING = 1200;
    public static final int PROVISIONAL_GAMES = 10;
    public static final int PROVISIONAL_K = 40;
    public static final int ESTABLISHED_K = 24;

    private final RatingSeasonRepository seasons;
    private final PlayerRatingRepository ratings;
    private final RatingChangeRepository changes;
    private final Clock clock;

    public RatingService(
            RatingSeasonRepository seasons,
            PlayerRatingRepository ratings,
            RatingChangeRepository changes,
            Clock clock) {
        this.seasons = seasons;
        this.ratings = ratings;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public void apply(MatchSummary match) {
        if (match.mode() != MatchMode.RANKED
                || match.phase() != MatchPhase.TERMINAL
                || match.terminalResult() == null
                || match.terminalResult().reason() == TerminalReason.NO_CONTEST
                || changes.existsByMatchId(match.matchId())) {
            return;
        }

        RatingSeasonEntity season = seasons.findFirstByActiveTrueOrderByStartsAtDesc()
                .orElseThrow(() -> new IllegalStateException("An active rating season is required"));
        UUID playerOneId = accountId(match, PlayerSide.PLAYER_ONE);
        UUID playerTwoId = accountId(match, PlayerSide.PLAYER_TWO);
        Instant now = clock.instant();
        PlayerRatingEntity playerOne = rating(playerOneId, season.getId(), now);
        PlayerRatingEntity playerTwo = rating(playerTwoId, season.getId(), now);

        double playerOneActual = actualScore(match.terminalResult(), PlayerSide.PLAYER_ONE);
        double playerTwoActual = 1.0 - playerOneActual;
        double playerOneExpected = expectedScore(playerOne.getRating(), playerTwo.getRating());
        double playerTwoExpected = 1.0 - playerOneExpected;
        int playerOneK = kFactor(playerOne.getRatedGames());
        int playerTwoK = kFactor(playerTwo.getRatedGames());
        double playerOneRaw = playerOneK * (playerOneActual - playerOneExpected);
        double playerTwoRaw = playerTwoK * (playerTwoActual - playerTwoExpected);
        int playerOneDelta = (int) Math.round((playerOneRaw - playerTwoRaw) / 2.0);
        int playerTwoDelta = -playerOneDelta;

        RatingChangeEntity playerOneChange = change(
                match.matchId(), season.getId(), playerOne, playerOneExpected,
                playerOneActual, playerOneDelta, playerOneK, now);
        RatingChangeEntity playerTwoChange = change(
                match.matchId(), season.getId(), playerTwo, playerTwoExpected,
                playerTwoActual, playerTwoDelta, playerTwoK, now);
        ratings.saveAll(List.of(playerOne, playerTwo));
        changes.saveAll(List.of(playerOneChange, playerTwoChange));
    }

    @Transactional(readOnly = true)
    public CurrentRating current(UUID userId) {
        RatingSeasonEntity season = seasons.findFirstByActiveTrueOrderByStartsAtDesc()
                .orElseThrow(() -> new IllegalStateException("An active rating season is required"));
        return ratings.findByIdUserIdAndIdSeasonId(userId, season.getId())
                .map(rating -> view(rating, season))
                .orElseGet(() -> initialView(season));
    }

    @Transactional(readOnly = true)
    public Optional<RatingChangeView> changeForMatch(UUID matchId, UUID userId) {
        return changes.findByMatchIdAndUserId(matchId, userId).map(this::view);
    }

    private PlayerRatingEntity rating(UUID userId, UUID seasonId, Instant now) {
        return ratings.findByIdUserIdAndIdSeasonId(userId, seasonId)
                .orElseGet(() -> new PlayerRatingEntity(userId, seasonId, now));
    }

    private RatingChangeEntity change(
            UUID matchId,
            UUID seasonId,
            PlayerRatingEntity rating,
            double expected,
            double actual,
            int delta,
            int kFactor,
            Instant now) {
        int preMatchRating = rating.getRating();
        rating.apply(delta, actual, now);
        return new RatingChangeEntity(
                UUID.randomUUID(),
                matchId,
                rating.getId().userId(),
                seasonId,
                preMatchRating,
                decimal(expected, 6),
                decimal(actual, 1),
                delta,
                rating.getRating(),
                kFactor,
                now);
    }

    private UUID accountId(MatchSummary match, PlayerSide side) {
        String playerId = match.participants().get(side);
        if (playerId == null) {
            throw new IllegalStateException("A ranked match requires two account participants");
        }
        try {
            return UUID.fromString(playerId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("A ranked participant is not an account", exception);
        }
    }

    private double actualScore(TerminalResult result, PlayerSide side) {
        if (result.winner() == null) {
            return 0.5;
        }
        return result.winner() == side ? 1.0 : 0.0;
    }

    private double expectedScore(int playerRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - playerRating) / 400.0));
    }

    private int kFactor(int ratedGames) {
        return ratedGames < PROVISIONAL_GAMES ? PROVISIONAL_K : ESTABLISHED_K;
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private CurrentRating view(PlayerRatingEntity rating, RatingSeasonEntity season) {
        CompetitiveTier tier = CompetitiveTier.forRating(rating.getRating());
        return new CurrentRating(
                rating.getRating(),
                tier,
                tier.label(),
                rating.getRatedGames(),
                rating.getWins(),
                rating.getLosses(),
                rating.getDraws(),
                rating.getRatedGames() < PROVISIONAL_GAMES,
                season.getId(),
                season.getName());
    }

    private CurrentRating initialView(RatingSeasonEntity season) {
        CompetitiveTier tier = CompetitiveTier.forRating(INITIAL_RATING);
        return new CurrentRating(
                INITIAL_RATING,
                tier,
                tier.label(),
                0,
                0,
                0,
                0,
                true,
                season.getId(),
                season.getName());
    }

    private RatingChangeView view(RatingChangeEntity change) {
        return new RatingChangeView(
                change.getMatchId(),
                change.getPreMatchRating(),
                change.getExpectedScore(),
                change.getActualScore(),
                change.getRatingDelta(),
                change.getPostMatchRating(),
                change.getKFactor(),
                change.getSeasonId(),
                change.getCalculatedAt());
    }

    public record CurrentRating(
            int rating,
            CompetitiveTier tier,
            String tierLabel,
            int ratedGames,
            int wins,
            int losses,
            int draws,
            boolean provisional,
            UUID seasonId,
            String seasonName) {
    }

    public record RatingChangeView(
            UUID matchId,
            int preMatchRating,
            BigDecimal expectedScore,
            BigDecimal actualScore,
            int ratingDelta,
            int postMatchRating,
            int kFactor,
            UUID seasonId,
            Instant calculatedAt) {
    }
}
