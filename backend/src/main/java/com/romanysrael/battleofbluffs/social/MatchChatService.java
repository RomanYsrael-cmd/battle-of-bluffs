package com.romanysrael.battleofbluffs.social;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchChatService.class);
    private static final int LOCK_STRIPES = 64;
    static final int MAXIMUM_MESSAGE_LENGTH = 500;

    private final MatchApplicationService matches;
    private final MatchChatMessageRepository messages;
    private final BlockRelationshipService blocks;
    private final UserAccountRepository accounts;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;
    private final int rateLimit;
    private final Duration rateWindow;
    private final Object[] matchLocks = IntStream.range(0, LOCK_STRIPES)
            .mapToObj(ignored -> new Object())
            .toArray();
    private final Map<RateKey, Deque<Instant>> recentMessages = new ConcurrentHashMap<>();

    public MatchChatService(
            MatchApplicationService matches,
            MatchChatMessageRepository messages,
            BlockRelationshipService blocks,
            UserAccountRepository accounts,
            SimpMessagingTemplate messaging,
            Clock clock,
            @Value("${app.rate-limit.chat.limit:5}") int rateLimit,
            @Value("${app.rate-limit.chat.window:10s}") Duration rateWindow) {
        this.matches = matches;
        this.messages = messages;
        this.blocks = blocks;
        this.accounts = accounts;
        this.messaging = messaging;
        this.clock = clock;
        if (rateLimit < 1 || rateWindow.isNegative() || rateWindow.isZero()) {
            throw new IllegalArgumentException("Chat rate limits must use positive values");
        }
        this.rateLimit = rateLimit;
        this.rateWindow = rateWindow;
    }

    public ChatMessageView send(UUID matchId, UUID senderId, String submittedBody) {
        List<UUID> participants = participants(matchId, senderId);
        UUID opponentId = opponent(participants, senderId);
        String body = validateBody(submittedBody);

        synchronized (matchLock(matchId)) {
            if (blocks.existsEitherDirection(senderId, opponentId)) {
                throw new ChatException("CHAT_BLOCKED", "Chat is unavailable for these participants.");
            }
            Instant now = clock.instant();
            requireRatePermit(matchId, senderId, now);
            MatchChatMessageEntity message = new MatchChatMessageEntity(
                    UUID.randomUUID(),
                    matchId,
                    senderId,
                    messages.maximumSequence(matchId) + 1,
                    body,
                    now);
            messages.saveAndFlush(message);
            publish(message, participants);
            return view(message, senderId);
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> history(UUID matchId, UUID requesterId, Long afterSequence) {
        participants(matchId, requesterId);
        Instant participantCycleStartedAt = matches.participantCycleStartedAt(
                matchId, requesterId.toString());
        List<MatchChatMessageEntity> history;
        if (afterSequence == null) {
            history = new ArrayList<>(messages.findTop100ByMatchIdOrderBySequenceNumberDesc(matchId));
            Collections.reverse(history);
        } else {
            history = messages
                    .findTop100ByMatchIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                            matchId,
                            Math.max(afterSequence, 0));
        }
        return history.stream()
                .filter(message -> participantCycleStartedAt == null
                        || !message.getCreatedAt().isBefore(participantCycleStartedAt))
                .map(message -> view(message, requesterId))
                .toList();
    }

    private List<UUID> participants(UUID matchId, UUID requesterId) {
        List<UUID> participants = matches.participantIds(matchId, requesterId.toString()).stream()
                .map(UUID::fromString)
                .toList();
        if (participants.size() != 2) {
            throw new ChatException("CHAT_UNAVAILABLE", "Chat opens after both players join.");
        }
        return participants;
    }

    private UUID opponent(List<UUID> participants, UUID requesterId) {
        return participants.stream()
                .filter(participantId -> !participantId.equals(requesterId))
                .findFirst()
                .orElseThrow(() -> new ChatException(
                        "CHAT_UNAVAILABLE", "An opponent is required for match chat."));
    }

    private String validateBody(String submittedBody) {
        if (submittedBody == null) {
            throw new ChatException("INVALID_CHAT_MESSAGE", "A plain-text message is required.");
        }
        String body = submittedBody.strip();
        if (body.isEmpty()) {
            throw new ChatException("INVALID_CHAT_MESSAGE", "A message cannot be empty.");
        }
        if (body.length() > MAXIMUM_MESSAGE_LENGTH) {
            throw new ChatException(
                    "INVALID_CHAT_MESSAGE", "A message cannot exceed 500 characters.");
        }
        return body;
    }

    private void requireRatePermit(UUID matchId, UUID senderId, Instant now) {
        Deque<Instant> attempts = recentMessages.computeIfAbsent(
                new RateKey(matchId, senderId), ignored -> new ArrayDeque<>());
        Instant windowStart = now.minus(rateWindow);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(windowStart)) {
            attempts.removeFirst();
        }
        if (attempts.size() >= rateLimit) {
            throw new ChatException(
                    "CHAT_RATE_LIMITED", "You can send up to five messages every ten seconds.");
        }
        attempts.addLast(now);
        if (recentMessages.size() > 10_000) {
            recentMessages.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                    || !entry.getValue().peekLast().isAfter(windowStart));
        }
    }

    private void publish(MatchChatMessageEntity message, List<UUID> participants) {
        for (UUID participantId : participants) {
            try {
                accounts.findById(participantId).ifPresent(account -> messaging.convertAndSendToUser(
                        account.getUsername(),
                        destination(message.getMatchId()),
                        view(message, participantId)));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Chat message {} was persisted but could not be delivered to participant {}",
                        message.getId(),
                        participantId,
                        exception);
            }
        }
    }

    private Object matchLock(UUID matchId) {
        int stripe = (matchId.hashCode() & Integer.MAX_VALUE) % matchLocks.length;
        return matchLocks[stripe];
    }

    private ChatMessageView view(MatchChatMessageEntity message, UUID requesterId) {
        UserAccountEntity sender = accounts.findById(message.getSenderId()).orElse(null);
        String displayName = sender == null || sender.getAccountStatus() == AccountStatus.DELETED
                ? "Former player"
                : sender.getDisplayName();
        return new ChatMessageView(
                message.getId(),
                message.getMatchId(),
                message.getSequenceNumber(),
                displayName,
                message.getSenderId().equals(requesterId),
                message.getBody(),
                message.getCreatedAt());
    }

    static String destination(UUID matchId) {
        return "/queue/matches/" + matchId + "/chat";
    }

    private record RateKey(UUID matchId, UUID senderId) {
    }

    public record ChatMessageView(
            UUID id,
            UUID matchId,
            long sequence,
            String senderDisplayName,
            boolean ownMessage,
            String body,
            Instant serverTimestamp) {
    }
}
