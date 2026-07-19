package com.romanysrael.battleofbluffs.game.websocket;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public final class MatchPresenceCoordinator {
    private final ObjectProvider<MatchApplicationService> matches;
    private final Map<Participant, Set<String>> sessionsByParticipant = new HashMap<>();
    private final Map<String, Set<Participant>> participantsBySession = new HashMap<>();

    public MatchPresenceCoordinator(ObjectProvider<MatchApplicationService> matches) {
        this.matches = matches;
    }

    public synchronized void subscribed(UUID matchId, String playerId, String sessionId) {
        Participant participant = new Participant(matchId, playerId);
        Set<String> sessions = sessionsByParticipant.computeIfAbsent(
                participant, ignored -> new HashSet<>());
        boolean firstConnection = sessions.isEmpty();
        if (!sessions.add(sessionId)) {
            return;
        }
        participantsBySession.computeIfAbsent(sessionId, ignored -> new HashSet<>())
                .add(participant);
        if (firstConnection) {
            matches.getObject().playerConnected(matchId, playerId);
        }
    }

    @EventListener
    public synchronized void disconnected(SessionDisconnectEvent event) {
        Set<Participant> participants = participantsBySession.remove(event.getSessionId());
        if (participants == null) {
            return;
        }
        for (Participant participant : participants) {
            Set<String> sessions = sessionsByParticipant.get(participant);
            if (sessions == null) {
                continue;
            }
            sessions.remove(event.getSessionId());
            if (sessions.isEmpty()) {
                sessionsByParticipant.remove(participant);
                matches.getObject().playerDisconnected(
                        participant.matchId(), participant.playerId());
            }
        }
    }

    private record Participant(UUID matchId, String playerId) {
    }
}
