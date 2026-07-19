package com.romanysrael.battleofbluffs.game.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class MatchPresenceCoordinatorTest {
    @Test
    void multipleBrowserSessionsRemainConnectedUntilTheLastSessionCloses() {
        MatchApplicationService matches = mock(MatchApplicationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MatchApplicationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(matches);
        MatchPresenceCoordinator presence = new MatchPresenceCoordinator(provider);
        UUID matchId = UUID.randomUUID();
        String playerId = UUID.randomUUID().toString();

        presence.subscribed(matchId, playerId, "session-one");
        presence.subscribed(matchId, playerId, "session-two");
        verify(matches).playerConnected(matchId, playerId);

        presence.disconnected(disconnect("session-one"));
        verify(matches, never()).playerDisconnected(matchId, playerId);

        presence.disconnected(disconnect("session-two"));
        verify(matches).playerDisconnected(matchId, playerId);
    }

    private static SessionDisconnectEvent disconnect(String sessionId) {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn(sessionId);
        return event;
    }
}
