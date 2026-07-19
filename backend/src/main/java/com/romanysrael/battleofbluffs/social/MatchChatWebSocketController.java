package com.romanysrael.battleofbluffs.social;

import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class MatchChatWebSocketController {
    private final MatchChatService chat;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public MatchChatWebSocketController(
            MatchChatService chat,
            SimpMessagingTemplate messaging,
            Clock clock) {
        this.chat = chat;
        this.messaging = messaging;
        this.clock = clock;
    }

    @MessageMapping("/matches/{matchId}/chat")
    public void send(
            @DestinationVariable UUID matchId,
            ChatRequest request,
            Authentication authentication) {
        AccountPrincipal principal = principal(authentication);
        try {
            chat.send(matchId, principal.userId(), request == null ? null : request.body());
        } catch (ChatException exception) {
            messaging.convertAndSendToUser(
                    principal.username(),
                    errorDestination(matchId),
                    new ChatError(exception.code(), exception.getMessage(), clock.instant()));
        }
    }

    private AccountPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal;
        }
        throw new ChatException("AUTHENTICATION_REQUIRED", "Sign in to use match chat.");
    }

    static String errorDestination(UUID matchId) {
        return MatchChatService.destination(matchId) + "/errors";
    }

    public record ChatRequest(String body) {
    }

    public record ChatError(String code, String message, Instant serverTimestamp) {
    }
}
