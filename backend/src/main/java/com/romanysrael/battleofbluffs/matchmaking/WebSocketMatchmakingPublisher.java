package com.romanysrael.battleofbluffs.matchmaking;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public final class WebSocketMatchmakingPublisher implements MatchmakingPublisher {
    private final SimpMessagingTemplate messaging;

    public WebSocketMatchmakingPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void found(String username, MatchmakingFound event) {
        messaging.convertAndSendToUser(username, "/queue/matchmaking", event);
    }
}
