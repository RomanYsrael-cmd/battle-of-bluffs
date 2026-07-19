package com.romanysrael.battleofbluffs.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.competition.CompetitiveTier;
import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.competition.RatingService.CurrentRating;
import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.InMemoryMatchRepository;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchViewMapper;
import com.romanysrael.battleofbluffs.game.application.TimerMode;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankedMatchmakingServiceTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final RatingService ratings = mock(RatingService.class);
    private final MatchmakingPublisher publisher = mock(MatchmakingPublisher.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-19T06:00:00Z"));
    private final MatchApplicationService matches = new MatchApplicationService(
            new InMemoryMatchRepository(), new PlayerMatchViewMapper());
    private final Map<UUID, Integer> playerRatings = new HashMap<>();
    private RankedMatchmakingService service;

    @BeforeEach
    void setUp() {
        when(ratings.current(any())).thenAnswer(invocation -> rating(
                playerRatings.getOrDefault(invocation.getArgument(0), 1200)));
        service = new RankedMatchmakingService(accounts, ratings, matches, publisher, clock);
    }

    @Test
    void onlyVerifiedActiveAccountsMayEnterRankedQueue() {
        UUID unverified = account("cadet", AccountStatus.UNVERIFIED);

        assertThatThrownBy(() -> service.enqueue(unverified))
                .isInstanceOf(MatchmakingException.class)
                .extracting(exception -> ((MatchmakingException) exception).code())
                .isEqualTo("EMAIL_VERIFICATION_REQUIRED");
    }

    @Test
    void duplicateQueueRequestReturnsOriginalEntryWithoutDuplicatingIt() {
        UUID player = account("marshal", AccountStatus.ACTIVE);

        var first = service.enqueue(player);
        clock.advance(Duration.ofSeconds(12));
        var retry = service.enqueue(player);

        assertThat(retry.queuedAt()).isEqualTo(first.queuedAt());
        assertThat(retry.elapsedSeconds()).isEqualTo(12);
        verify(publisher, never()).found(any(), any());
    }

    @Test
    void accountAlreadyInAnActiveMatchCannotQueue() {
        UUID player = account("occupied", AccountStatus.ACTIVE);
        matches.createMatch(new CreateMatchCommand(player.toString()));

        assertThatThrownBy(() -> service.enqueue(player))
                .isInstanceOf(MatchmakingException.class)
                .extracting(exception -> ((MatchmakingException) exception).code())
                .isEqualTo("OPEN_MATCH_EXISTS");

        MatchmakingException conflict = org.junit.jupiter.api.Assertions.assertThrows(
                MatchmakingException.class, () -> service.enqueue(player));
        RankedMatchmakingService.OpenMatchRecovery recovery =
                (RankedMatchmakingService.OpenMatchRecovery) conflict.context();
        assertThat(recovery.blockingMatches()).hasSize(1);
        assertThat(recovery.blockingMatches().get(0).canResume()).isTrue();
        assertThat(recovery.blockingMatches().get(0).canCancel()).isTrue();
    }

    @Test
    void compatiblePlayersCreateOnePersistedRankedMatchWithFixedTimerAndNoRoomCode() {
        UUID first = account("first", AccountStatus.ACTIVE);
        UUID second = account("second", AccountStatus.ACTIVE);

        assertThat(service.enqueue(first).state()).isEqualTo("QUEUED");
        var secondStatus = service.enqueue(second);

        assertThat(secondStatus.state()).isEqualTo("MATCH_FOUND");
        PlayerMatchView firstView = matches.getView(secondStatus.matchId(), first.toString());
        PlayerMatchView secondView = matches.getView(secondStatus.matchId(), second.toString());
        assertThat(firstView.mode()).isEqualTo(MatchMode.RANKED);
        assertThat(firstView.timerMode()).isEqualTo(TimerMode.STANDARD_15_PLUS_5);
        assertThat(firstView.roomCode()).isNull();
        assertThat(secondView.requestingSide()).isNotEqualTo(firstView.requestingSide());
        assertThat(service.current(first).state()).isEqualTo("MATCH_FOUND");
        assertThat(service.current(second).matchId()).isEqualTo(secondStatus.matchId());
        verify(publisher, org.mockito.Mockito.times(2)).found(any(), any());
    }

    @Test
    void oldestCompatibleEntryIsSelectedInsteadOfAnOlderIncompatibleOpponent() {
        UUID oldest = account("oldest", AccountStatus.ACTIVE);
        UUID incompatible = account("incompatible", AccountStatus.ACTIVE);
        UUID compatible = account("compatible", AccountStatus.ACTIVE);
        playerRatings.put(oldest, 1200);
        playerRatings.put(incompatible, 1500);
        playerRatings.put(compatible, 1210);

        service.enqueue(oldest);
        service.enqueue(incompatible);
        var status = service.enqueue(compatible);

        assertThat(status.state()).isEqualTo("MATCH_FOUND");
        assertThat(matches.getView(status.matchId(), oldest.toString())).isNotNull();
        assertThat(service.current(incompatible).state()).isEqualTo("QUEUED");
    }

    @Test
    void searchRangeExpandsByFiftyEveryThirtySecondsAndStopsAtSixHundred() {
        UUID first = account("range-one", AccountStatus.ACTIVE);
        UUID second = account("range-two", AccountStatus.ACTIVE);
        playerRatings.put(first, 1200);
        playerRatings.put(second, 1450);

        service.enqueue(first);
        service.enqueue(second);
        assertThat(service.current(first).searchRange()).isEqualTo(200);

        clock.advance(Duration.ofSeconds(30));
        service.scan();
        assertThat(service.current(first).state()).isEqualTo("MATCH_FOUND");

        UUID farOne = account("far-one", AccountStatus.ACTIVE);
        UUID farTwo = account("far-two", AccountStatus.ACTIVE);
        playerRatings.put(farOne, 1000);
        playerRatings.put(farTwo, 1650);
        service.enqueue(farOne);
        service.enqueue(farTwo);
        clock.advance(Duration.ofHours(1));
        service.scan();
        assertThat(service.current(farOne).state()).isEqualTo("QUEUED");
        assertThat(service.current(farOne).searchRange()).isEqualTo(600);
    }

    @Test
    void cancelRemovesOnlyTheRequestingPlayersQueueEntry() {
        UUID player = account("cancel", AccountStatus.ACTIVE);
        service.enqueue(player);

        assertThat(service.cancel(player).state()).isEqualTo("IDLE");
        assertThat(service.current(player).state()).isEqualTo("IDLE");
    }

    @Test
    void websocketDeliveryFailureCannotUndoPersistedMatchOrQueueRemoval() {
        UUID first = account("broker-one", AccountStatus.ACTIVE);
        UUID second = account("broker-two", AccountStatus.ACTIVE);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).found(any(), any());

        service.enqueue(first);
        var result = service.enqueue(second);

        assertThat(result.state()).isEqualTo("MATCH_FOUND");
        assertThat(matches.getView(result.matchId(), first.toString()).mode())
                .isEqualTo(MatchMode.RANKED);
    }

    private UUID account(String username, AccountStatus status) {
        UUID id = UUID.randomUUID();
        UserAccountEntity account = new UserAccountEntity(
                id,
                username,
                username,
                username + "@example.test",
                username + "@example.test",
                "{noop}password",
                "Player " + username,
                status,
                clock.instant());
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        return id;
    }

    private CurrentRating rating(int value) {
        CompetitiveTier tier = CompetitiveTier.forRating(value);
        return new CurrentRating(
                value, tier, tier.label(), 0, 0, 0, 0, true,
                UUID.randomUUID(), "Season One");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
