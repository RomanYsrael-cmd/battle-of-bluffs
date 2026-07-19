package com.romanysrael.battleofbluffs.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.Commands.CancelMatchCommand;
import com.romanysrael.battleofbluffs.game.application.InMemoryMatchRepository;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchViewMapper;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateJpaRepository;
import com.romanysrael.battleofbluffs.user.AccountService;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileHistoryAuthorizationTest {
    @Test
    void activeMatchHistoryGivesParticipantSafeViewAndHidesExistenceFromOutsider() {
        MatchApplicationService matches = new MatchApplicationService(
                new InMemoryMatchRepository(), new PlayerMatchViewMapper());
        UUID hostId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID matchId = matches.createMatch(new CreateMatchCommand(hostId.toString())).view().matchId();
        MatchAggregateJpaRepository aggregates = mock(MatchAggregateJpaRepository.class);
        when(aggregates.findById(matchId)).thenReturn(Optional.empty());
        ProfileService profiles = new ProfileService(
                mock(UserAccountRepository.class),
                mock(AccountService.class),
                matches,
                aggregates,
                mock(RatingService.class));

        ProfileService.MatchHistoryView participant = profiles.history(matchId, hostId);
        assertThat(participant.participant()).isTrue();
        assertThat(participant.participantView()).isNotNull();
        assertThatThrownBy(() -> profiles.history(matchId, outsiderId))
                .isInstanceOf(com.romanysrael.battleofbluffs.user.AccountException.class)
                .extracting(exception -> ((com.romanysrael.battleofbluffs.user.AccountException) exception).code())
                .isEqualTo("MATCH_NOT_FOUND");
    }

    @Test
    void cancelledWaitingRoomIsVisibleAsCancelledHistoryButDoesNotCountAsAGame() {
        MatchApplicationService matches = new MatchApplicationService(
                new InMemoryMatchRepository(), new PlayerMatchViewMapper());
        UUID hostId = UUID.randomUUID();
        var created = matches.createMatch(new CreateMatchCommand(hostId.toString()));
        matches.cancelMatch(new CancelMatchCommand(
                UUID.randomUUID(), created.view().matchId(), hostId.toString(), created.version()));

        UserAccountRepository accountRepository = mock(UserAccountRepository.class);
        UserAccountEntity account = new UserAccountEntity(
                hostId,
                "cancelled_host",
                "cancelled_host",
                "cancelled@example.test",
                "cancelled@example.test",
                "encoded",
                "Cancelled Host",
                AccountStatus.ACTIVE,
                Instant.parse("2026-07-19T00:00:00Z"));
        when(accountRepository.findById(hostId)).thenReturn(Optional.of(account));
        MatchAggregateJpaRepository aggregates = mock(MatchAggregateJpaRepository.class);
        RatingService ratings = mock(RatingService.class);
        ProfileService profiles = new ProfileService(
                accountRepository,
                mock(AccountService.class),
                matches,
                aggregates,
                ratings);

        ProfileService.PrivateProfileView profile = profiles.me(hostId);

        assertThat(profile.statistics().totalGames()).isZero();
        assertThat(profile.statistics().casualGames()).isZero();
        assertThat(profile.recentMatches()).singleElement()
                .extracting(ProfileService.MatchListItem::outcome)
                .isEqualTo("CANCELLED");
    }

    @Test
    void extremeRecentMatchPageReturnsEmptyWithoutIntegerOverflow() {
        UUID userId = UUID.randomUUID();
        ProfileService profiles = new ProfileService(
                mock(UserAccountRepository.class),
                mock(AccountService.class),
                new MatchApplicationService(new InMemoryMatchRepository(), new PlayerMatchViewMapper()),
                mock(MatchAggregateJpaRepository.class),
                mock(RatingService.class));

        ProfileService.PageView<ProfileService.MatchListItem> page =
                profiles.recent(userId, Integer.MAX_VALUE, 100);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
    }
}
