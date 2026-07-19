package com.romanysrael.battleofbluffs.game.websocket;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationException;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class MatchSubscriptionInterceptor implements ChannelInterceptor {
    private static final Pattern MATCH_SUBSCRIPTION = Pattern.compile(
            "^/user/queue/matches/([0-9a-fA-F-]{36})(?:/chat(?:/errors)?)?$");
    private static final Pattern MATCH_CHAT_SEND = Pattern.compile(
            "^/app/matches/([0-9a-fA-F-]{36})/chat$");
    private static final String MATCHMAKING_SUBSCRIPTION = "/user/queue/matchmaking";

    private final ObjectProvider<MatchApplicationService> matches;
    private final ObjectProvider<AccountUserDetailsService> accounts;
    private final MatchPresenceCoordinator presence;

    public MatchSubscriptionInterceptor(
            ObjectProvider<MatchApplicationService> matches,
            ObjectProvider<AccountUserDetailsService> accounts,
            MatchPresenceCoordinator presence) {
        this.matches = matches;
        this.accounts = accounts;
        this.presence = presence;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE
                && accessor.getCommand() != StompCommand.SEND) {
            return message;
        }

        Authentication authentication = authentication(accessor);
        String destination = accessor.getDestination();
        if (accessor.getCommand() == StompCommand.SUBSCRIBE
                && MATCHMAKING_SUBSCRIPTION.equals(destination)) {
            return message;
        }
        Pattern allowedPattern = accessor.getCommand() == StompCommand.SEND
                ? MATCH_CHAT_SEND
                : MATCH_SUBSCRIPTION;
        Matcher destinationMatch = allowedPattern.matcher(destination == null ? "" : destination);
        if (!destinationMatch.matches()) {
            throw new AccessDeniedException("WebSocket destination is not allowed");
        }

        UUID matchId;
        try {
            matchId = UUID.fromString(destinationMatch.group(1));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("WebSocket destination is not allowed", exception);
        }

        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        if (!principal.isEnabled()
                || !accounts.getObject().loadUserByUsername(principal.username()).isEnabled()) {
            throw new AccessDeniedException("Account is unavailable for WebSocket messaging");
        }
        try {
            matches.getObject().getView(matchId, principal.userId().toString());
        } catch (MatchApplicationException exception) {
            throw new AccessDeniedException("WebSocket destination is not allowed", exception);
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new AccessDeniedException("WebSocket session is required");
        }
        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        if (!sent) {
            return;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }
        String destination = accessor.getDestination();
        Matcher destinationMatch = MATCH_SUBSCRIPTION.matcher(
                destination == null ? "" : destination);
        if (!destinationMatch.matches()
                || !destination.equals("/user/queue/matches/" + destinationMatch.group(1))) {
            return;
        }
        if (!(accessor.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return;
        }
        String sessionId = accessor.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        presence.subscribed(
                UUID.fromString(destinationMatch.group(1)),
                principal.userId().toString(),
                sessionId);
    }

    private Authentication authentication(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal) {
            return authentication;
        }
        throw new AccessDeniedException("Authentication is required for WebSocket messaging");
    }
}
