package com.romanysrael.battleofbluffs.social;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchModerationService {
    private static final int MAXIMUM_REFERENCES = 20;

    private final MatchApplicationService matches;
    private final BlockRelationshipService blocks;
    private final PlayerReportRepository reports;
    private final MatchChatMessageRepository messages;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public MatchModerationService(
            MatchApplicationService matches,
            BlockRelationshipService blocks,
            PlayerReportRepository reports,
            MatchChatMessageRepository messages,
            UserAccountRepository accounts,
            Clock clock) {
        this.matches = matches;
        this.blocks = blocks;
        this.reports = reports;
        this.messages = messages;
        this.accounts = accounts;
        this.clock = clock;
    }

    public ModerationStatus status(UUID matchId, UUID requesterId) {
        UUID opponentId = opponent(matchId, requesterId);
        return new ModerationStatus(
                displayName(opponentId),
                blocks.blockedBy(requesterId, opponentId));
    }

    public ModerationStatus block(UUID matchId, UUID requesterId) {
        UUID opponentId = opponent(matchId, requesterId);
        blocks.block(requesterId, opponentId);
        return new ModerationStatus(displayName(opponentId), true);
    }

    public ModerationStatus unblock(UUID matchId, UUID requesterId) {
        UUID opponentId = opponent(matchId, requesterId);
        blocks.unblock(requesterId, opponentId);
        return new ModerationStatus(displayName(opponentId), false);
    }

    @Transactional
    public ReportReceipt report(
            UUID matchId,
            UUID reporterId,
            ReportCategory category,
            String submittedComment,
            List<UUID> submittedMessageReferences) {
        if (category == null) {
            throw new ChatException("INVALID_REPORT", "A report category is required.");
        }
        UUID reportedUserId = opponent(matchId, reporterId);
        String comment = normalizeComment(submittedComment);
        List<UUID> references = validateReferences(
                matchId,
                reportedUserId,
                submittedMessageReferences == null ? List.of() : submittedMessageReferences);
        UUID reportId = UUID.randomUUID();
        reports.save(new PlayerReportEntity(
                reportId,
                reporterId,
                reportedUserId,
                matchId,
                category,
                comment,
                references.stream().map(UUID::toString).collect(Collectors.joining(",")),
                clock.instant()));
        return new ReportReceipt(reportId, "Report received.");
    }

    private UUID opponent(UUID matchId, UUID requesterId) {
        return matches.participantIds(matchId, requesterId.toString()).stream()
                .map(UUID::fromString)
                .filter(participantId -> !participantId.equals(requesterId))
                .findFirst()
                .orElseThrow(() -> new ChatException(
                        "OPPONENT_UNAVAILABLE", "An opponent has not joined this match."));
    }

    private String normalizeComment(String submittedComment) {
        if (submittedComment == null || submittedComment.isBlank()) {
            return null;
        }
        String comment = submittedComment.strip();
        if (comment.length() > 1000) {
            throw new ChatException("INVALID_REPORT", "A report comment cannot exceed 1000 characters.");
        }
        return comment;
    }

    private List<UUID> validateReferences(
            UUID matchId,
            UUID reportedUserId,
            List<UUID> submittedReferences) {
        List<UUID> referenceIds = submittedReferences.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        if (referenceIds.size() > MAXIMUM_REFERENCES) {
            throw new ChatException("INVALID_REPORT", "At most 20 chat messages may be referenced.");
        }
        List<MatchChatMessageEntity> referencedMessages = messages.findAllById(referenceIds);
        boolean allPermitted = referencedMessages.size() == referenceIds.size()
                && referencedMessages.stream().allMatch(message ->
                        message.getMatchId().equals(matchId)
                                && message.getSenderId().equals(reportedUserId));
        if (!allPermitted) {
            throw new ChatException(
                    "INVALID_REPORT", "Chat references must be opponent messages from this match.");
        }
        return referenceIds;
    }

    private String displayName(UUID userId) {
        UserAccountEntity account = accounts.findById(userId).orElse(null);
        if (account == null || account.getAccountStatus() == AccountStatus.DELETED) {
            return "Former player";
        }
        return account.getDisplayName();
    }

    public record ModerationStatus(String opponentDisplayName, boolean blockedByYou) {
    }

    public record ReportReceipt(UUID reportId, String message) {
    }
}
