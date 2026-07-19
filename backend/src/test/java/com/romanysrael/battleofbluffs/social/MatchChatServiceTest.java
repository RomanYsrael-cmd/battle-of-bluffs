package com.romanysrael.battleofbluffs.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.InMemoryMatchRepository;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchViewMapper;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationException;
import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class MatchChatServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T04:00:00Z");

    private final MatchApplicationService matches = org.mockito.Mockito.mock(MatchApplicationService.class);
    private final MatchChatMessageRepository messages = org.mockito.Mockito.mock(MatchChatMessageRepository.class);
    private final BlockRelationshipService blocks = org.mockito.Mockito.mock(BlockRelationshipService.class);
    private final UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final SimpMessagingTemplate messaging = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
    private final UUID matchId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID opponentId = UUID.randomUUID();
    private MatchChatService chat;

    @BeforeEach
    void setUp() {
        when(matches.participantIds(matchId, senderId.toString()))
                .thenReturn(List.of(senderId.toString(), opponentId.toString()));
        when(messages.maximumSequence(matchId)).thenReturn(7L);
        when(accounts.findById(senderId)).thenReturn(Optional.of(account(senderId, "sender", "Sender")));
        when(accounts.findById(opponentId)).thenReturn(Optional.of(account(opponentId, "opponent", "Opponent")));
        chat = new MatchChatService(
                matches,
                messages,
                blocks,
                accounts,
                messaging,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validPlainTextIsTrimmedPersistedInOrderAndDeliveredAsParticipantSpecificViews() {
        MatchChatService.ChatMessageView result = chat.send(matchId, senderId, "  <b>Hello</b>  ");

        ArgumentCaptor<MatchChatMessageEntity> saved = ArgumentCaptor.forClass(MatchChatMessageEntity.class);
        verify(messages).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBody()).isEqualTo("<b>Hello</b>");
        assertThat(saved.getValue().getSequenceNumber()).isEqualTo(8);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(result.ownMessage()).isTrue();
        assertThat(result.serverTimestamp()).isEqualTo(NOW);
        verify(messaging).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("sender"),
                org.mockito.ArgumentMatchers.eq("/queue/matches/" + matchId + "/chat"),
                any(MatchChatService.ChatMessageView.class));
        verify(messaging).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("opponent"),
                org.mockito.ArgumentMatchers.eq("/queue/matches/" + matchId + "/chat"),
                any(MatchChatService.ChatMessageView.class));
    }

    @Test
    void emptyAndOverlongMessagesAreRejectedBeforePersistence() {
        assertThatThrownBy(() -> chat.send(matchId, senderId, " \n\t "))
                .isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).code())
                .isEqualTo("INVALID_CHAT_MESSAGE");
        assertThatThrownBy(() -> chat.send(matchId, senderId, "x".repeat(501)))
                .isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).code())
                .isEqualTo("INVALID_CHAT_MESSAGE");
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void sixthMessageInsideTenSecondsIsRateLimitedWithoutBeingPersisted() {
        for (int index = 0; index < 5; index++) {
            chat.send(matchId, senderId, "message " + index);
        }

        assertThatThrownBy(() -> chat.send(matchId, senderId, "message 6"))
                .isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).code())
                .isEqualTo("CHAT_RATE_LIMITED");
        verify(messages, org.mockito.Mockito.times(5)).saveAndFlush(any());
    }

    @Test
    void blockInEitherDirectionStopsFurtherPersistenceAndDelivery() {
        when(blocks.existsEitherDirection(senderId, opponentId)).thenReturn(true);

        assertThatThrownBy(() -> chat.send(matchId, senderId, "hello"))
                .isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).code())
                .isEqualTo("CHAT_BLOCKED");
        verify(messages, never()).saveAndFlush(any());
        verify(messaging, never()).convertAndSendToUser(any(), any(), any(Object.class));
    }

    @Test
    void historyChecksMembershipAndReturnsOnlyTheRequestedMatchSequence() {
        MatchChatMessageEntity message = new MatchChatMessageEntity(
                UUID.randomUUID(), matchId, opponentId, 11, "hello", NOW);
        when(messages.findTop100ByMatchIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(matchId, 10))
                .thenReturn(List.of(message));

        List<MatchChatService.ChatMessageView> history = chat.history(matchId, senderId, 10L);

        assertThat(history).singleElement().satisfies(view -> {
            assertThat(view.sequence()).isEqualTo(11);
            assertThat(view.ownMessage()).isFalse();
            assertThat(view.senderDisplayName()).isEqualTo("Opponent");
        });
        verify(matches).participantIds(matchId, senderId.toString());
    }

    @Test
    void nonparticipantCannotReadAnotherPrivateMatchChatHistory() {
        MatchApplicationService realMatches = new MatchApplicationService(
                new InMemoryMatchRepository(), new PlayerMatchViewMapper());
        UUID hostId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        var created = realMatches.createMatch(new CreateMatchCommand(hostId.toString()));
        realMatches.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), guestId.toString(), 1));
        MatchChatService securedChat = new MatchChatService(
                realMatches,
                messages,
                blocks,
                accounts,
                messaging,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> securedChat.history(created.view().matchId(), outsiderId, null))
                .isInstanceOf(MatchApplicationException.class)
                .hasMessageContaining("Player does not belong to match");
        verify(messages, never()).findTop100ByMatchIdOrderBySequenceNumberDesc(any());
    }

    private static UserAccountEntity account(UUID id, String username, String displayName) {
        return new UserAccountEntity(
                id,
                username,
                username,
                username + "@example.test",
                username + "@example.test",
                "{noop}password",
                displayName,
                AccountStatus.ACTIVE,
                NOW);
    }
}
