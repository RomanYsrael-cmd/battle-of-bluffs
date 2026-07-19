package com.romanysrael.battleofbluffs.game.websocket;

import com.romanysrael.battleofbluffs.game.application.MatchUpdatePublisher;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public final class WebSocketMatchUpdatePublisher implements MatchUpdatePublisher {
    private final SimpMessagingTemplate messaging;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public WebSocketMatchUpdatePublisher(
            SimpMessagingTemplate messaging,
            UserAccountRepository accounts,
            Clock clock) {
        this.messaging = messaging;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public void publish(MatchUpdate update) {
        Instant serverTimestamp = clock.instant();
        update.playerViews().forEach((playerId, view) -> account(playerId).ifPresent(account -> {
            MatchUpdateEnvelope envelope = new MatchUpdateEnvelope(
                    update.type(),
                    update.matchId(),
                    update.sequence(),
                    view.version(),
                    serverTimestamp,
                    view);
            messaging.convertAndSendToUser(
                    account.getUsername(), destination(update.matchId()), envelope);
        }));
    }

    private java.util.Optional<UserAccountEntity> account(String playerId) {
        try {
            return accounts.findById(UUID.fromString(playerId));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    static String destination(UUID matchId) {
        return "/queue/matches/" + matchId;
    }
}
