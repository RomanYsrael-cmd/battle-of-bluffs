package com.romanysrael.battleofbluffs.matchmaking;

import static org.mockito.Mockito.verify;

import com.romanysrael.battleofbluffs.matchmaking.MatchmakingPublisher.MatchmakingFound;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class WebSocketMatchmakingPublisherTest {
    @Test
    void foundEventIsSentOnlyToTheMatchedAccountsUserDestination() {
        SimpMessagingTemplate messaging = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        WebSocketMatchmakingPublisher publisher = new WebSocketMatchmakingPublisher(messaging);
        MatchmakingFound event = new MatchmakingFound(
                UUID.randomUUID(), "Opponent", 1210, Instant.parse("2026-07-19T06:00:00Z"), null);

        publisher.found("marshal", event);

        verify(messaging).convertAndSendToUser("marshal", "/queue/matchmaking", event);
    }
}
