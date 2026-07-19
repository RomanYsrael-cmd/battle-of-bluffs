package com.romanysrael.battleofbluffs.matchmaking;

import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.MatchSummary;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.RankedMatch;
import com.romanysrael.battleofbluffs.matchmaking.MatchmakingPublisher.MatchmakingFound;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RankedMatchmakingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RankedMatchmakingService.class);
    static final int INITIAL_RANGE = 200;
    static final int RANGE_STEP = 50;
    static final int MAXIMUM_RANGE = 600;
    static final Duration RANGE_INTERVAL = Duration.ofSeconds(30);

    private final UserAccountRepository accounts;
    private final RatingService ratings;
    private final MatchApplicationService matches;
    private final MatchmakingPublisher publisher;
    private final Clock clock;
    private final Map<UUID, QueueEntry> queued = new LinkedHashMap<>();

    public RankedMatchmakingService(
            UserAccountRepository accounts,
            RatingService ratings,
            MatchApplicationService matches,
            MatchmakingPublisher publisher,
            Clock clock) {
        this.accounts = accounts;
        this.ratings = ratings;
        this.matches = matches;
        this.publisher = publisher;
        this.clock = clock;
    }

    public synchronized QueueStatus enqueue(UUID userId) {
        QueueEntry existing = queued.get(userId);
        if (existing != null) {
            return status(existing, clock.instant());
        }
        MatchSummary activeRanked = matches.activeRankedMatch(userId.toString()).orElse(null);
        if (activeRanked != null) {
            return found(activeRanked);
        }
        List<MatchApplicationService.CurrentMatchSummary> blockingMatches =
                matches.currentMatches(userId.toString());
        if (!blockingMatches.isEmpty()) {
            throw new MatchmakingException(
                    "OPEN_MATCH_EXISTS",
                    "You already have a game in progress.",
                    new OpenMatchRecovery(blockingMatches));
        }
        UserAccountEntity account = verifiedAccount(userId);
        Instant now = clock.instant();
        QueueEntry entry = new QueueEntry(
                userId,
                account.getUsername(),
                account.getDisplayName(),
                ratings.current(userId).rating(),
                now);
        queued.put(userId, entry);
        pairCompatibleEntries(now);
        QueueEntry stillQueued = queued.get(userId);
        return stillQueued == null
                ? matches.activeRankedMatch(userId.toString()).map(this::found)
                        .orElseThrow(() -> new IllegalStateException("Matched game was not persisted"))
                : status(stillQueued, now);
    }

    public synchronized QueueStatus cancel(UUID userId) {
        queued.remove(userId);
        return current(userId);
    }

    public synchronized QueueStatus current(UUID userId) {
        QueueEntry entry = queued.get(userId);
        if (entry != null) {
            return status(entry, clock.instant());
        }
        return matches.activeRankedMatch(userId.toString())
                .map(this::found)
                .orElseGet(QueueStatus::idle);
    }

    @Scheduled(fixedDelayString = "${app.matchmaking.scan-ms:1000}")
    public synchronized void scan() {
        pairCompatibleEntries(clock.instant());
    }

    private void pairCompatibleEntries(Instant now) {
        removeIneligiblePlayers();
        boolean matched;
        do {
            matched = false;
            List<QueueEntry> entries = new ArrayList<>(queued.values());
            for (int firstIndex = 0; firstIndex < entries.size() && !matched; firstIndex++) {
                QueueEntry first = entries.get(firstIndex);
                for (int secondIndex = firstIndex + 1; secondIndex < entries.size(); secondIndex++) {
                    QueueEntry second = entries.get(secondIndex);
                    if (compatible(first, second, now)) {
                        createMatch(first, second, now);
                        matched = true;
                        break;
                    }
                }
            }
        } while (matched);
    }

    private void removeIneligiblePlayers() {
        queued.entrySet().removeIf(entry -> {
            UserAccountEntity account = accounts.findById(entry.getKey()).orElse(null);
            return account == null
                    || !account.isEmailVerified()
                    || account.getAccountStatus() != AccountStatus.ACTIVE
                    || matches.hasActiveMatch(entry.getKey().toString());
        });
    }

    private boolean compatible(QueueEntry first, QueueEntry second, Instant now) {
        if (first.userId().equals(second.userId())) {
            return false;
        }
        int difference = Math.abs(first.rating() - second.rating());
        return difference <= Math.min(searchRange(first, now), searchRange(second, now));
    }

    private void createMatch(QueueEntry first, QueueEntry second, Instant now) {
        RankedMatch match = matches.createRankedMatch(
                first.userId().toString(), second.userId().toString());
        queued.remove(first.userId());
        queued.remove(second.userId());
        publishFound(first.username(), new MatchmakingFound(
                match.matchId(), second.displayName(), second.rating(), now, match.playerOneView()));
        publishFound(second.username(), new MatchmakingFound(
                match.matchId(), first.displayName(), first.rating(), now, match.playerTwoView()));
    }

    private void publishFound(String username, MatchmakingFound event) {
        try {
            publisher.found(username, event);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Ranked match {} was persisted but its matchmaking notification to {} failed",
                    event.matchId(),
                    username,
                    exception);
        }
    }

    private UserAccountEntity verifiedAccount(UUID userId) {
        UserAccountEntity account = accounts.findById(userId)
                .orElseThrow(() -> new MatchmakingException("ACCOUNT_NOT_FOUND", "Account not found."));
        if (!account.isEmailVerified() || account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new MatchmakingException(
                    "EMAIL_VERIFICATION_REQUIRED",
                    "Verify your email before joining ranked matchmaking.");
        }
        return account;
    }

    private QueueStatus status(QueueEntry entry, Instant now) {
        long elapsedSeconds = Math.max(0, Duration.between(entry.queuedAt(), now).toSeconds());
        return new QueueStatus(
                "QUEUED",
                entry.queuedAt(),
                elapsedSeconds,
                searchRange(entry, now),
                entry.rating(),
                null);
    }

    private QueueStatus found(MatchSummary match) {
        return new QueueStatus("MATCH_FOUND", null, 0, 0, 0, match.matchId());
    }

    private int searchRange(QueueEntry entry, Instant now) {
        long intervals = Math.max(0, Duration.between(entry.queuedAt(), now).toSeconds())
                / RANGE_INTERVAL.toSeconds();
        return (int) Math.min(MAXIMUM_RANGE, INITIAL_RANGE + intervals * RANGE_STEP);
    }

    record QueueEntry(
            UUID userId,
            String username,
            String displayName,
            int rating,
            Instant queuedAt) {
    }

    public record QueueStatus(
            String state,
            Instant queuedAt,
            long elapsedSeconds,
            int searchRange,
            int rating,
            UUID matchId) {
        static QueueStatus idle() {
            return new QueueStatus("IDLE", null, 0, 0, 0, null);
        }
    }

    public record OpenMatchRecovery(
            List<MatchApplicationService.CurrentMatchSummary> blockingMatches) {
        public OpenMatchRecovery {
            blockingMatches = List.copyOf(blockingMatches);
        }
    }
}
