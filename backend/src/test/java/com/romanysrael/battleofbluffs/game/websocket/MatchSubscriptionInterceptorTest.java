package com.romanysrael.battleofbluffs.game.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.InMemoryMatchRepository;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchViewMapper;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class MatchSubscriptionInterceptorTest {
    private MatchSubscriptionInterceptor interceptor;
    private MatchApplicationService matches;
    private UUID memberId;
    private UUID matchId;

    @BeforeEach
    void setUp() {
        matches = new MatchApplicationService(
                new InMemoryMatchRepository(), new PlayerMatchViewMapper());
        memberId = UUID.randomUUID();
        matchId = matches.createMatch(new CreateMatchCommand(memberId.toString())).view().matchId();
        @SuppressWarnings("unchecked")
        ObjectProvider<MatchApplicationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(matches);
        interceptor = new MatchSubscriptionInterceptor(
                provider, mock(MatchPresenceCoordinator.class));
    }

    @Test
    void participantMaySubscribeToTheirOwnMatchUpdates() {
        assertDoesNotThrow(() -> interceptor.preSend(
                subscription(matchId, memberId), mock(MessageChannel.class)));
    }

    @Test
    void authenticatedNonparticipantCannotSubscribeToAnotherMatch() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                subscription(matchId, UUID.randomUUID()), mock(MessageChannel.class)));
    }

    @Test
    void anonymousAndUnrecognizedDestinationsAreRejected() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                subscription(matchId, null), mock(MessageChannel.class)));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                subscription("/user/queue/admin", memberId), mock(MessageChannel.class)));
    }

    @Test
    void clientsCannotPublishForgedMessagesIntoBrokerDestinations() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/queue/matches/" + matchId);
        Message<byte[]> forgedMessage = MessageBuilder.createMessage(
                "forged".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                headers.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                forgedMessage, mock(MessageChannel.class)));
    }

    private static Message<byte[]> subscription(UUID matchId, UUID userId) {
        return subscription("/user/queue/matches/" + matchId, userId);
    }

    private static Message<byte[]> subscription(String destination, UUID userId) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        headers.setSessionId("session-1");
        headers.setSubscriptionId("match-updates");
        if (userId != null) {
            AccountPrincipal principal = new AccountPrincipal(
                    userId, "user-" + userId, "Player", AccountStatus.ACTIVE, "encoded");
            headers.setUser(UsernamePasswordAuthenticationToken.authenticated(
                    principal, principal.getPassword(), principal.getAuthorities()));
        }
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
