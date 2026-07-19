package com.romanysrael.battleofbluffs.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.MatchSummary;
import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.application.TimerMode;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class RatingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T05:00:00Z");

    private final RatingSeasonRepository seasons = org.mockito.Mockito.mock(RatingSeasonRepository.class);
    private final PlayerRatingRepository ratings = org.mockito.Mockito.mock(PlayerRatingRepository.class);
    private final RatingChangeRepository changes = org.mockito.Mockito.mock(RatingChangeRepository.class);
    private final RatingSeasonEntity season = org.mockito.Mockito.mock(RatingSeasonEntity.class);
    private final UUID seasonId = UUID.randomUUID();
    private final UUID playerOneId = UUID.randomUUID();
    private final UUID playerTwoId = UUID.randomUUID();
    private RatingService service;

    @BeforeEach
    void setUp() {
        when(season.getId()).thenReturn(seasonId);
        when(season.getName()).thenReturn("Season One");
        when(seasons.findFirstByActiveTrueOrderByStartsAtDesc()).thenReturn(Optional.of(season));
        when(ratings.findByIdUserIdAndIdSeasonId(any(), any())).thenReturn(Optional.empty());
        service = new RatingService(
                seasons,
                ratings,
                changes,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void equalProvisionalPlayersReceiveBalancedPairTransactionalEloChanges() {
        MatchSummary match = rankedResult(TerminalResult.win(
                PlayerSide.PLAYER_ONE, TerminalReason.RESIGNATION));

        service.apply(match);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RatingChangeEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(changes).saveAll(saved.capture());
        List<RatingChangeEntity> ratingChanges = new ArrayList<>();
        saved.getValue().forEach(ratingChanges::add);
        assertThat(ratingChanges).hasSize(2);
        assertThat(ratingChanges).extracting(RatingChangeEntity::getRatingDelta)
                .containsExactlyInAnyOrder(20, -20);
        assertThat(ratingChanges).extracting(RatingChangeEntity::getKFactor)
                .containsOnly(RatingService.PROVISIONAL_K);
        assertThat(ratingChanges).extracting(RatingChangeEntity::getPreMatchRating)
                .containsOnly(RatingService.INITIAL_RATING);
        assertThat(ratingChanges).extracting(RatingChangeEntity::getCalculatedAt)
                .containsOnly(NOW);
        assertThat(ratingChanges.stream().mapToInt(RatingChangeEntity::getRatingDelta).sum())
                .isZero();
        verify(ratings).saveAll(any());
    }

    @Test
    void existingMatchLedgerMakesRetriesReconnectsAndRestartReconciliationNoOps() {
        MatchSummary match = rankedResult(TerminalResult.win(
                PlayerSide.PLAYER_TWO, TerminalReason.DISCONNECT_FORFEIT));
        when(changes.existsByMatchId(match.matchId())).thenReturn(true);

        service.apply(match);

        verify(seasons, never()).findFirstByActiveTrueOrderByStartsAtDesc();
        verify(ratings, never()).saveAll(any());
        verify(changes, never()).saveAll(any());
    }

    @Test
    void casualAndNoContestMatchesNeverAlterRatings() {
        MatchSummary casual = new MatchSummary(
                UUID.randomUUID(), MatchMode.CASUAL, TimerMode.CASUAL_UNTIMED,
                MatchPhase.TERMINAL,
                Map.of(PlayerSide.PLAYER_ONE, playerOneId.toString(),
                        PlayerSide.PLAYER_TWO, playerTwoId.toString()),
                TerminalResult.win(PlayerSide.PLAYER_ONE, TerminalReason.FLAG_CAPTURE),
                12);
        MatchSummary noContest = rankedResult(TerminalResult.draw(TerminalReason.NO_CONTEST));

        service.apply(casual);
        service.apply(noContest);

        verify(changes, never()).existsByMatchId(any());
        verify(ratings, never()).saveAll(any());
    }

    @ParameterizedTest
    @CsvSource({
            "899,CADET", "900,PRIVATE", "1099,PRIVATE", "1100,SERGEANT",
            "1300,LIEUTENANT", "1500,CAPTAIN", "1700,COLONEL",
            "1900,GENERAL", "2100,GRAND_GENERAL"
    })
    void competitiveTierBoundariesAreDeterministic(int rating, CompetitiveTier expected) {
        assertThat(CompetitiveTier.forRating(rating)).isEqualTo(expected);
    }

    private MatchSummary rankedResult(TerminalResult result) {
        return new MatchSummary(
                UUID.randomUUID(),
                MatchMode.RANKED,
                TimerMode.STANDARD_15_PLUS_5,
                MatchPhase.TERMINAL,
                Map.of(
                        PlayerSide.PLAYER_ONE, playerOneId.toString(),
                        PlayerSide.PLAYER_TWO, playerTwoId.toString()),
                result,
                20);
    }
}
