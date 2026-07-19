package com.romanysrael.battleofbluffs.competition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T05:00:00Z");

    @Test
    void seasonalBoardRequiresFiveGamesExcludesUnavailableAccountsAndReturnsOwnRow() {
        RatingSeasonRepository seasons = org.mockito.Mockito.mock(RatingSeasonRepository.class);
        PlayerRatingRepository ratings = org.mockito.Mockito.mock(PlayerRatingRepository.class);
        UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
        RatingService ratingService = org.mockito.Mockito.mock(RatingService.class);
        RatingSeasonEntity season = org.mockito.Mockito.mock(RatingSeasonEntity.class);
        UUID seasonId = UUID.randomUUID();
        UUID eligibleId = UUID.randomUUID();
        UUID suspendedId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        PlayerRatingEntity eligible = rating(eligibleId, seasonId, 5);
        PlayerRatingEntity suspended = rating(suspendedId, seasonId, 8);
        PlayerRatingEntity requester = rating(requesterId, seasonId, 2);
        when(season.getId()).thenReturn(seasonId);
        when(season.getName()).thenReturn("Season One");
        when(seasons.findFirstByActiveTrueOrderByStartsAtDesc()).thenReturn(Optional.of(season));
        when(ratings.findByIdSeasonIdOrderByRatingDescRatedGamesDescIdUserIdAsc(seasonId))
                .thenReturn(List.of(eligible, suspended, requester));
        when(ratings.findByIdUserIdAndIdSeasonId(requesterId, seasonId))
                .thenReturn(Optional.of(requester));
        when(accounts.findById(eligibleId)).thenReturn(Optional.of(account(
                eligibleId, "eligible", AccountStatus.ACTIVE)));
        when(accounts.findById(suspendedId)).thenReturn(Optional.of(account(
                suspendedId, "suspended", AccountStatus.SUSPENDED)));
        when(accounts.findById(requesterId)).thenReturn(Optional.of(account(
                requesterId, "requester", AccountStatus.ACTIVE)));
        LeaderboardService service = new LeaderboardService(
                seasons, ratings, accounts, ratingService);

        LeaderboardService.LeaderboardPage page = service.seasonal(requesterId, 0, 25);

        assertThat(page.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.username()).isEqualTo("eligible");
            assertThat(entry.position()).isEqualTo(1);
            assertThat(entry.eligible()).isTrue();
        });
        assertThat(page.ownEntry().username()).isEqualTo("requester");
        assertThat(page.ownEntry().position()).isNull();
        assertThat(page.ownEntry().eligible()).isFalse();
        assertThat(page.minimumGames()).isEqualTo(5);
    }

    private static PlayerRatingEntity rating(UUID userId, UUID seasonId, int games) {
        PlayerRatingEntity rating = new PlayerRatingEntity(userId, seasonId, NOW);
        for (int index = 0; index < games; index++) {
            rating.apply(0, 0.5, NOW);
        }
        return rating;
    }

    private static UserAccountEntity account(UUID id, String username, AccountStatus status) {
        return new UserAccountEntity(
                id,
                username,
                username,
                username + "@example.test",
                username + "@example.test",
                "{noop}password",
                username,
                status,
                NOW);
    }
}
