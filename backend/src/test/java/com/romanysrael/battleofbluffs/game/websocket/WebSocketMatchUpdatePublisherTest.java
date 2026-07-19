package com.romanysrael.battleofbluffs.game.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.application.MatchUpdatePublisher.MatchUpdate;
import com.romanysrael.battleofbluffs.game.application.MatchUpdatePublisher.UpdateType;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import com.romanysrael.battleofbluffs.game.application.TimerMode;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

class WebSocketMatchUpdatePublisherTest {
    @Test
    void sameAuthoritativeUpdateSendsDifferentSecrecySafeViewToEachParticipant() throws Exception {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UUID matchId = UUID.randomUUID();
        UUID playerOneId = UUID.randomUUID();
        UUID playerTwoId = UUID.randomUUID();
        UUID playerOnePieceId = UUID.randomUUID();
        UUID playerTwoPieceId = UUID.randomUUID();
        UUID playerOnePublicId = UUID.randomUUID();
        UUID playerTwoPublicId = UUID.randomUUID();
        when(accounts.findById(playerOneId)).thenReturn(Optional.of(
                account(playerOneId, "alpha")));
        when(accounts.findById(playerTwoId)).thenReturn(Optional.of(
                account(playerTwoId, "bravo")));

        PlayerMatchView playerOneView = view(
                matchId, playerOneId, PlayerSide.PLAYER_ONE,
                playerOnePieceId, Rank.FLAG, playerTwoPublicId);
        PlayerMatchView playerTwoView = view(
                matchId, playerTwoId, PlayerSide.PLAYER_TWO,
                playerTwoPieceId, Rank.SPY, playerOnePublicId);
        WebSocketMatchUpdatePublisher publisher = new WebSocketMatchUpdatePublisher(
                messaging,
                accounts,
                Clock.fixed(Instant.parse("2026-07-19T10:15:30Z"), ZoneOffset.UTC));

        publisher.publish(new MatchUpdate(
                matchId,
                8,
                UpdateType.MATCH_STARTED,
                Map.of(playerOneId.toString(), playerOneView, playerTwoId.toString(), playerTwoView)));

        ArgumentCaptor<Object> firstPayload = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> secondPayload = ArgumentCaptor.forClass(Object.class);
        String destination = "/queue/matches/" + matchId;
        verify(messaging).convertAndSendToUser(eq("alpha"), eq(destination), firstPayload.capture());
        verify(messaging).convertAndSendToUser(eq("bravo"), eq(destination), secondPayload.capture());
        MatchUpdateEnvelope first = (MatchUpdateEnvelope) firstPayload.getValue();
        MatchUpdateEnvelope second = (MatchUpdateEnvelope) secondPayload.getValue();

        assertEquals(8, first.sequence());
        assertEquals(8, first.version());
        assertEquals(Instant.parse("2026-07-19T10:15:30Z"), first.serverTimestamp());
        assertNotEquals(first.view(), second.view());
        assertEquals(Rank.FLAG, first.view().ownPieces().get(0).rank());
        assertEquals(Rank.SPY, second.view().ownPieces().get(0).rank());
        assertFalse(JsonMapper.builder().build().valueToTree(first)
                .path("view").path("opponentPieces").path(0).has("rank"));
        assertFalse(JsonMapper.builder().build().valueToTree(second)
                .path("view").path("opponentPieces").path(0).has("rank"));
    }

    private static UserAccountEntity account(UUID id, String username) {
        return new UserAccountEntity(
                id,
                username,
                username,
                username + "@example.test",
                username + "@example.test",
                "encoded",
                username,
                AccountStatus.ACTIVE,
                Instant.EPOCH);
    }

    private static PlayerMatchView view(
            UUID matchId,
            UUID playerId,
            PlayerSide side,
            UUID ownPieceId,
            Rank ownRank,
            UUID opponentPublicId) {
        return new PlayerMatchView(
                matchId,
                "ROOM42",
                8,
                MatchPhase.ACTIVE,
                MatchMode.CASUAL,
                TimerMode.CASUAL_UNTIMED,
                playerId.toString(),
                side,
                true,
                true,
                true,
                true,
                PlayerSide.PLAYER_ONE,
                List.of(new PlayerMatchView.OwnPieceView(
                        ownPieceId, ownRank, new Position(2, 0), true)),
                List.of(new PlayerMatchView.OpponentPieceView(
                        opponentPublicId, new Position(5, 0))),
                List.of(),
                null,
                null,
                List.of());
    }
}
