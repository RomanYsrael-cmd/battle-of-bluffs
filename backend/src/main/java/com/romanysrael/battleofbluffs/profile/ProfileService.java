package com.romanysrael.battleofbluffs.profile;

import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.competition.RatingService.CurrentRating;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.MatchSummary;
import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import com.romanysrael.battleofbluffs.game.application.TimerMode;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.TerminalReason;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateEntity;
import com.romanysrael.battleofbluffs.game.persistence.MatchAggregateJpaRepository;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountService;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserAccountRepository accounts;
    private final AccountService accountService;
    private final MatchApplicationService matches;
    private final MatchAggregateJpaRepository aggregates;
    private final RatingService ratings;

    public ProfileService(
            UserAccountRepository accounts,
            AccountService accountService,
            MatchApplicationService matches,
            MatchAggregateJpaRepository aggregates,
            RatingService ratings) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.matches = matches;
        this.aggregates = aggregates;
        this.ratings = ratings;
    }

    @Transactional(readOnly = true)
    public PrivateProfileView me(UUID userId) {
        UserAccountEntity account = account(userId);
        List<MatchSummary> summaries = playerMatches(userId);
        CurrentRating rating = ratings.current(userId);
        return new PrivateProfileView(
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                account.getCreatedAt(),
                account.isEmailVerified(),
                stats(userId, summaries),
                rating,
                recent(userId, summaries, 0, 5).items());
    }

    @Transactional(readOnly = true)
    public PublicProfileView publicProfile(String username) {
        UserAccountEntity account = accounts.findByNormalizedUsername(
                        username.strip().toLowerCase(Locale.ROOT))
                .filter(candidate -> candidate.getAccountStatus() != AccountStatus.DELETED)
                .orElseThrow(() -> new AccountException("ACCOUNT_NOT_FOUND", "Profile not found."));
        List<MatchSummary> summaries = playerMatches(account.getId());
        List<MatchSummary> publicSummaries = summaries.stream()
                .filter(summary -> summary.phase() == MatchPhase.TERMINAL)
                .toList();
        return new PublicProfileView(
                account.getUsername(),
                account.getDisplayName(),
                account.getCreatedAt(),
                stats(account.getId(), publicSummaries),
                ratings.current(account.getId()),
                recent(account.getId(), publicSummaries, 0, 5).items());
    }

    public PrivateProfileView update(UUID userId, String displayName) {
        accountService.updateDisplayName(userId, displayName);
        return me(userId);
    }

    @Transactional(readOnly = true)
    public PageView<MatchListItem> recent(UUID userId, int page, int size) {
        validatePage(page, size);
        return recent(userId, playerMatches(userId), page, size);
    }

    public MatchHistoryView history(UUID matchId, UUID requesterId) {
        MatchSummary summary = matches.summary(matchId);
        boolean participant = summary.participants().containsValue(requesterId.toString());
        MatchAggregateEntity aggregate = aggregates.findById(matchId).orElse(null);
        if (summary.mode() == MatchMode.RANKED && summary.phase() == MatchPhase.TERMINAL) {
            ratings.apply(summary);
        }
        PlayerMatchView participantView = participant
                ? matches.getView(matchId, requesterId.toString())
                : null;
        return new MatchHistoryView(
                publicSummary(summary, aggregate),
                participant,
                participantView,
                participant ? ratings.changeForMatch(matchId, requesterId).orElse(null) : null);
    }

    private PageView<MatchListItem> recent(
            UUID userId,
            List<MatchSummary> summaries,
            int page,
            int size) {
        Map<UUID, MatchAggregateEntity> metadata = aggregates.findAllById(
                        summaries.stream().map(MatchSummary::matchId).toList())
                .stream()
                .collect(Collectors.toMap(MatchAggregateEntity::id, Function.identity()));
        List<MatchListItem> ordered = summaries.stream()
                .sorted(Comparator.comparing(
                        summary -> updatedAt(summary.matchId(), metadata),
                        Comparator.reverseOrder()))
                .map(summary -> listItem(userId, summary, metadata.get(summary.matchId())))
                .toList();
        int from = Math.min(page * size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        int totalPages = ordered.isEmpty() ? 0 : (ordered.size() + size - 1) / size;
        return new PageView<>(
                ordered.subList(from, to), page, size, ordered.size(), totalPages);
    }

    private MatchListItem listItem(
            UUID userId,
            MatchSummary summary,
            MatchAggregateEntity metadata) {
        PlayerSide side = sideOf(summary, userId);
        String opponentId = summary.participants().get(side.opponent());
        UserAccountEntity opponent = accountOrNull(opponentId);
        Integer ratingDelta = ratings.changeForMatch(summary.matchId(), userId)
                .map(RatingService.RatingChangeView::ratingDelta)
                .orElse(null);
        return new MatchListItem(
                summary.matchId(),
                summary.mode(),
                summary.timerMode(),
                summary.phase(),
                outcome(summary.terminalResult(), side),
                opponent == null ? "Waiting for opponent" : safeDisplayName(opponent),
                opponent == null || opponent.getAccountStatus() == AccountStatus.DELETED
                        ? null
                        : opponent.getUsername(),
                metadata == null ? null : metadata.createdAt(),
                metadata == null ? null : metadata.terminalAt(),
                summary.terminalResult(),
                summary.acceptedMoveCount(),
                ratingDelta);
    }

    private ProfileStats stats(UUID userId, List<MatchSummary> summaries) {
        int total = 0;
        int ranked = 0;
        int casual = 0;
        int wins = 0;
        int losses = 0;
        int draws = 0;
        int noContests = 0;
        for (MatchSummary summary : summaries) {
            if (summary.phase() != MatchPhase.TERMINAL || summary.terminalResult() == null) {
                continue;
            }
            total++;
            if (summary.mode() == MatchMode.RANKED) {
                ranked++;
            } else {
                casual++;
            }
            switch (outcome(summary.terminalResult(), sideOf(summary, userId))) {
                case "WIN" -> wins++;
                case "LOSS" -> losses++;
                case "DRAW" -> draws++;
                case "NO_CONTEST" -> noContests++;
                default -> { }
            }
        }
        int decided = wins + losses + draws;
        double winRate = decided == 0 ? 0 : (double) wins / decided;
        return new ProfileStats(total, ranked, casual, wins, losses, draws, noContests, winRate);
    }

    private List<MatchSummary> playerMatches(UUID userId) {
        String playerId = userId.toString();
        List<MatchSummary> summaries = new ArrayList<>();
        for (MatchSummary summary : matches.summaries()) {
            if (summary.participants().containsValue(playerId)) {
                summaries.add(summary);
            }
        }
        return summaries;
    }

    private PublicMatchSummary publicSummary(
            MatchSummary summary,
            MatchAggregateEntity aggregate) {
        return new PublicMatchSummary(
                summary.matchId(),
                summary.mode(),
                summary.timerMode(),
                summary.phase(),
                summary.phase() == MatchPhase.TERMINAL ? summary.terminalResult() : null,
                summary.phase() == MatchPhase.TERMINAL ? summary.acceptedMoveCount() : null,
                aggregate == null ? null : aggregate.createdAt(),
                aggregate == null ? null : aggregate.terminalAt());
    }

    private PlayerSide sideOf(MatchSummary summary, UUID userId) {
        return summary.participants().entrySet().stream()
                .filter(entry -> entry.getValue().equals(userId.toString()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AccountException(
                        "MATCH_ACCESS_INVALID", "Account is not a participant."));
    }

    private String outcome(TerminalResult result, PlayerSide side) {
        if (result == null) {
            return "IN_PROGRESS";
        }
        if (result.reason() == TerminalReason.NO_CONTEST) {
            return "NO_CONTEST";
        }
        if (result.winner() == null) {
            return "DRAW";
        }
        return result.winner() == side ? "WIN" : "LOSS";
    }

    private Instant updatedAt(UUID matchId, Map<UUID, MatchAggregateEntity> metadata) {
        MatchAggregateEntity aggregate = metadata.get(matchId);
        return aggregate == null ? Instant.EPOCH : aggregate.updatedAt();
    }

    private UserAccountEntity account(UUID userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> new AccountException("ACCOUNT_NOT_FOUND", "Account not found."));
    }

    private UserAccountEntity accountOrNull(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return accounts.findById(UUID.fromString(userId)).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String safeDisplayName(UserAccountEntity account) {
        return account.getAccountStatus() == AccountStatus.DELETED
                ? "Former player"
                : account.getDisplayName();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AccountException(
                    "INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    public record ProfileStats(
            int totalGames,
            int rankedGames,
            int casualGames,
            int wins,
            int losses,
            int draws,
            int noContests,
            double winRate) {
    }

    public record PrivateProfileView(
            String username,
            String displayName,
            String email,
            Instant joinedAt,
            boolean emailVerified,
            ProfileStats statistics,
            CurrentRating rating,
            List<MatchListItem> recentMatches) {
    }

    public record PublicProfileView(
            String username,
            String displayName,
            Instant joinedAt,
            ProfileStats statistics,
            CurrentRating rating,
            List<MatchListItem> recentMatches) {
    }

    public record MatchListItem(
            UUID matchId,
            MatchMode mode,
            TimerMode timerMode,
            MatchPhase phase,
            String outcome,
            String opponentDisplayName,
            String opponentUsername,
            Instant createdAt,
            Instant terminalAt,
            TerminalResult terminalResult,
            int acceptedMoveCount,
            Integer ratingDelta) {
    }

    public record PageView<T>(
            List<T> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }

    public record PublicMatchSummary(
            UUID matchId,
            MatchMode mode,
            TimerMode timerMode,
            MatchPhase phase,
            TerminalResult terminalResult,
            Integer acceptedMoveCount,
            Instant createdAt,
            Instant terminalAt) {
    }

    public record MatchHistoryView(
            PublicMatchSummary summary,
            boolean participant,
            PlayerMatchView participantView,
            RatingService.RatingChangeView ratingChange) {
    }
}
