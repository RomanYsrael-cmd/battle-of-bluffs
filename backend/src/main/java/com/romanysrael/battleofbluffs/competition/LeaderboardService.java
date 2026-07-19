package com.romanysrael.battleofbluffs.competition;

import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardService {
    public static final int MINIMUM_PUBLIC_GAMES = 5;

    private final RatingSeasonRepository seasons;
    private final PlayerRatingRepository ratings;
    private final UserAccountRepository accounts;
    private final RatingService ratingService;

    public LeaderboardService(
            RatingSeasonRepository seasons,
            PlayerRatingRepository ratings,
            UserAccountRepository accounts,
            RatingService ratingService) {
        this.seasons = seasons;
        this.ratings = ratings;
        this.accounts = accounts;
        this.ratingService = ratingService;
    }

    @Transactional(readOnly = true)
    public LeaderboardPage seasonal(UUID requesterId, int page, int size) {
        validatePage(page, size);
        RatingSeasonEntity season = seasons.findFirstByActiveTrueOrderByStartsAtDesc()
                .orElseThrow(() -> new IllegalStateException("An active rating season is required"));
        List<LeaderboardEntry> eligible = new ArrayList<>();
        for (PlayerRatingEntity rating
                : ratings.findByIdSeasonIdOrderByRatingDescRatedGamesDescIdUserIdAsc(season.getId())) {
            UserAccountEntity account = accounts.findById(rating.getId().userId()).orElse(null);
            if (account != null && visible(account) && rating.getRatedGames() >= MINIMUM_PUBLIC_GAMES) {
                eligible.add(entry(account, rating, true, null));
            }
        }
        List<LeaderboardEntry> ranked = positions(eligible);
        LeaderboardEntry own = ownSeasonal(requesterId, season, ranked);
        return page("SEASONAL", season.getName(), ranked, own, page, size);
    }

    @Transactional(readOnly = true)
    public LeaderboardPage allTime(UUID requesterId, int page, int size) {
        validatePage(page, size);
        Map<UUID, Totals> totals = new HashMap<>();
        for (PlayerRatingEntity rating : ratings.findAll()) {
            totals.computeIfAbsent(rating.getId().userId(), ignored -> new Totals())
                    .add(rating);
        }
        List<LeaderboardEntry> eligible = totals.entrySet().stream()
                .filter(entry -> entry.getValue().ratedGames >= MINIMUM_PUBLIC_GAMES)
                .map(entry -> allTimeEntry(entry.getKey(), entry.getValue()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(LeaderboardEntry::wins).reversed()
                        .thenComparing(Comparator.comparingInt(LeaderboardEntry::ratedGames).reversed())
                        .thenComparing(LeaderboardEntry::username))
                .toList();
        List<LeaderboardEntry> ranked = positions(eligible);
        LeaderboardEntry own = ownAllTime(requesterId, totals.get(requesterId), ranked);
        RatingSeasonEntity season = seasons.findFirstByActiveTrueOrderByStartsAtDesc().orElse(null);
        return page(
                "ALL_TIME",
                season == null ? null : season.getName(),
                ranked,
                own,
                page,
                size);
    }

    private LeaderboardEntry ownSeasonal(
            UUID requesterId,
            RatingSeasonEntity season,
            List<LeaderboardEntry> ranked) {
        LeaderboardEntry rankedEntry = ranked.stream()
                .filter(entry -> entry.userId().equals(requesterId))
                .findFirst()
                .orElse(null);
        if (rankedEntry != null) {
            return rankedEntry;
        }
        UserAccountEntity account = accounts.findById(requesterId).orElse(null);
        if (account == null) {
            return null;
        }
        return ratings.findByIdUserIdAndIdSeasonId(requesterId, season.getId())
                .map(rating -> entry(account, rating, false, null))
                .orElseGet(() -> initialEntry(account));
    }

    private LeaderboardEntry ownAllTime(
            UUID requesterId,
            Totals totals,
            List<LeaderboardEntry> ranked) {
        LeaderboardEntry rankedEntry = ranked.stream()
                .filter(entry -> entry.userId().equals(requesterId))
                .findFirst()
                .orElse(null);
        if (rankedEntry != null) {
            return rankedEntry;
        }
        UserAccountEntity account = accounts.findById(requesterId).orElse(null);
        if (account == null || totals == null) {
            return account == null ? null : initialEntry(account);
        }
        RatingService.CurrentRating current = ratingService.current(requesterId);
        return new LeaderboardEntry(
                requesterId,
                account.getUsername(),
                account.getDisplayName(),
                current.rating(),
                current.tier(),
                current.tierLabel(),
                totals.ratedGames,
                totals.wins,
                totals.losses,
                totals.draws,
                winRate(totals.wins, totals.ratedGames),
                current.provisional(),
                false,
                null);
    }

    private LeaderboardEntry allTimeEntry(UUID userId, Totals totals) {
        UserAccountEntity account = accounts.findById(userId).orElse(null);
        if (account == null || !visible(account)) {
            return null;
        }
        RatingService.CurrentRating current = ratingService.current(userId);
        return new LeaderboardEntry(
                userId,
                account.getUsername(),
                account.getDisplayName(),
                current.rating(),
                current.tier(),
                current.tierLabel(),
                totals.ratedGames,
                totals.wins,
                totals.losses,
                totals.draws,
                winRate(totals.wins, totals.ratedGames),
                current.provisional(),
                true,
                null);
    }

    private LeaderboardEntry entry(
            UserAccountEntity account,
            PlayerRatingEntity rating,
            boolean eligible,
            Integer position) {
        CompetitiveTier tier = CompetitiveTier.forRating(rating.getRating());
        return new LeaderboardEntry(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                rating.getRating(),
                tier,
                tier.label(),
                rating.getRatedGames(),
                rating.getWins(),
                rating.getLosses(),
                rating.getDraws(),
                winRate(rating.getWins(), rating.getRatedGames()),
                rating.getRatedGames() < RatingService.PROVISIONAL_GAMES,
                eligible,
                position);
    }

    private LeaderboardEntry initialEntry(UserAccountEntity account) {
        CompetitiveTier tier = CompetitiveTier.forRating(RatingService.INITIAL_RATING);
        return new LeaderboardEntry(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                RatingService.INITIAL_RATING,
                tier,
                tier.label(),
                0,
                0,
                0,
                0,
                0,
                true,
                false,
                null);
    }

    private List<LeaderboardEntry> positions(List<LeaderboardEntry> entries) {
        List<LeaderboardEntry> positioned = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            LeaderboardEntry entry = entries.get(index);
            positioned.add(new LeaderboardEntry(
                    entry.userId(), entry.username(), entry.displayName(), entry.rating(),
                    entry.tier(), entry.tierLabel(), entry.ratedGames(), entry.wins(),
                    entry.losses(), entry.draws(), entry.winRate(), entry.provisional(),
                    true, index + 1));
        }
        return positioned;
    }

    private LeaderboardPage page(
            String scope,
            String seasonName,
            List<LeaderboardEntry> entries,
            LeaderboardEntry own,
            int page,
            int size) {
        int from = Math.min(page * size, entries.size());
        int to = Math.min(from + size, entries.size());
        int totalPages = entries.isEmpty() ? 0 : (entries.size() + size - 1) / size;
        return new LeaderboardPage(
                scope,
                seasonName,
                entries.subList(from, to),
                own,
                page,
                size,
                entries.size(),
                totalPages,
                MINIMUM_PUBLIC_GAMES);
    }

    private boolean visible(UserAccountEntity account) {
        return account.getAccountStatus() != AccountStatus.SUSPENDED
                && account.getAccountStatus() != AccountStatus.DELETED;
    }

    private double winRate(int wins, int games) {
        return games == 0 ? 0 : (double) wins / games;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AccountException(
                    "INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private static final class Totals {
        private int ratedGames;
        private int wins;
        private int losses;
        private int draws;

        void add(PlayerRatingEntity rating) {
            ratedGames += rating.getRatedGames();
            wins += rating.getWins();
            losses += rating.getLosses();
            draws += rating.getDraws();
        }
    }

    public record LeaderboardEntry(
            UUID userId,
            String username,
            String displayName,
            int rating,
            CompetitiveTier tier,
            String tierLabel,
            int ratedGames,
            int wins,
            int losses,
            int draws,
            double winRate,
            boolean provisional,
            boolean eligible,
            Integer position) {
    }

    public record LeaderboardPage(
            String scope,
            String seasonName,
            List<LeaderboardEntry> entries,
            LeaderboardEntry ownEntry,
            int page,
            int size,
            long totalItems,
            int totalPages,
            int minimumGames) {
    }
}
