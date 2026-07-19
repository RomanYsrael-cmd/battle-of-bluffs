package com.romanysrael.battleofbluffs.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
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

class MatchModerationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T04:00:00Z");

    private final MatchApplicationService matches = org.mockito.Mockito.mock(MatchApplicationService.class);
    private final BlockRelationshipService blocks = org.mockito.Mockito.mock(BlockRelationshipService.class);
    private final PlayerReportRepository reports = org.mockito.Mockito.mock(PlayerReportRepository.class);
    private final MatchChatMessageRepository messages = org.mockito.Mockito.mock(MatchChatMessageRepository.class);
    private final UserAccountRepository accounts = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final UUID matchId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();
    private final UUID opponentId = UUID.randomUUID();
    private MatchModerationService moderation;

    @BeforeEach
    void setUp() {
        when(matches.participantIds(matchId, reporterId.toString()))
                .thenReturn(List.of(reporterId.toString(), opponentId.toString()));
        when(accounts.findById(opponentId)).thenReturn(Optional.of(new UserAccountEntity(
                opponentId, "opponent", "opponent", "opponent@example.test", "opponent@example.test",
                "{noop}password", "Opponent", AccountStatus.ACTIVE, NOW)));
        moderation = new MatchModerationService(
                matches,
                blocks,
                reports,
                messages,
                accounts,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void blockAndUnblockAlwaysTargetTheAuthenticatedParticipantsOpponent() {
        MatchModerationService.ModerationStatus blocked = moderation.block(matchId, reporterId);
        MatchModerationService.ModerationStatus unblocked = moderation.unblock(matchId, reporterId);

        verify(blocks).block(reporterId, opponentId);
        verify(blocks).unblock(reporterId, opponentId);
        assertThat(blocked.blockedByYou()).isTrue();
        assertThat(unblocked.blockedByYou()).isFalse();
    }

    @Test
    void reportPersistsOnlyOpponentMessagesFromTheSameMatch() {
        UUID messageId = UUID.randomUUID();
        when(messages.findAllById(List.of(messageId))).thenReturn(List.of(
                new MatchChatMessageEntity(messageId, matchId, opponentId, 1, "evidence", NOW)));

        MatchModerationService.ReportReceipt receipt = moderation.report(
                matchId,
                reporterId,
                ReportCategory.HARASSMENT,
                "  repeated abuse  ",
                List.of(messageId));

        ArgumentCaptor<PlayerReportEntity> saved = ArgumentCaptor.forClass(PlayerReportEntity.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getReporterId()).isEqualTo(reporterId);
        assertThat(saved.getValue().getReportedUserId()).isEqualTo(opponentId);
        assertThat(saved.getValue().getMatchId()).isEqualTo(matchId);
        assertThat(saved.getValue().getComment()).isEqualTo("repeated abuse");
        assertThat(saved.getValue().getChatMessageReferences()).isEqualTo(messageId.toString());
        assertThat(receipt.reportId()).isEqualTo(saved.getValue().getId());
    }

    @Test
    void reportRejectsForeignOrSelfAuthoredChatReferences() {
        UUID messageId = UUID.randomUUID();
        when(messages.findAllById(List.of(messageId))).thenReturn(List.of(
                new MatchChatMessageEntity(messageId, matchId, reporterId, 1, "own", NOW)));

        assertThatThrownBy(() -> moderation.report(
                matchId, reporterId, ReportCategory.OTHER, null, List.of(messageId)))
                .isInstanceOf(ChatException.class)
                .extracting(exception -> ((ChatException) exception).code())
                .isEqualTo("INVALID_REPORT");
        verify(reports, never()).save(any());
    }
}
