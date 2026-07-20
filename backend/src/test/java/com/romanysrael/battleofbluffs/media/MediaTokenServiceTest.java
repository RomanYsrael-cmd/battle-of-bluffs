package com.romanysrael.battleofbluffs.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.social.BlockRelationshipService;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MediaTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final UUID REQUESTER = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID OPPONENT = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID MATCH = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final Instant PARTICIPANT_CYCLE = Instant.parse("2026-07-20T11:55:00Z");
    private final MatchApplicationService matches = mock(MatchApplicationService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final BlockRelationshipService blocks = mock(BlockRelationshipService.class);
    private final MediaTokenIssuer issuer = mock(MediaTokenIssuer.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void eligibleMatch() {
        when(accounts.findById(REQUESTER)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(matches.mediaContext(MATCH, REQUESTER.toString())).thenReturn(
                new MatchApplicationService.MediaMatchContext(
                        MATCH, OPPONENT.toString(), PARTICIPANT_CYCLE, false, null));
        when(issuer.issue(any(), any(), any(), any())).thenReturn("signed.jwt");
    }

    @Test
    void remainsProxyableForTheReadOnlyTransactionBoundary() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(MediaTokenService.class.getModifiers())).isFalse();
        assertThat(MediaTokenService.class.getMethod("issue", UUID.class, UUID.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }

    @Test
    void derivesStableOpaqueScopeAndReturnsOnlyTheExpectedPublicFields() {
        MediaTokenService service = service(enabledProperties(), 10);

        MediaTokenService.MediaTokenResponse first = service.issue(MATCH, REQUESTER);
        MediaTokenService.MediaTokenResponse second = service.issue(MATCH, REQUESTER);

        assertThat(first.token()).isEqualTo("signed.jwt");
        assertThat(first.enabled()).isTrue();
        assertThat(first.url()).isEqualTo("wss://example.livekit.cloud");
        assertThat(first.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(first.room().matchId()).isEqualTo(MATCH);
        assertThat(first.room().participantCountLimit()).isEqualTo(2);
        ArgumentCaptor<String> room = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> identity = ArgumentCaptor.forClass(String.class);
        verify(issuer, org.mockito.Mockito.times(2)).issue(
                room.capture(), identity.capture(), org.mockito.ArgumentMatchers.eq("Player One"),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        assertThat(room.getAllValues()).containsOnly(room.getValue());
        assertThat(identity.getAllValues()).containsOnly(identity.getValue());
        assertThat(room.getValue()).startsWith("gotg-match-").doesNotContain(MATCH.toString());
        assertThat(identity.getValue()).startsWith("gotg-player-")
                .doesNotContain(REQUESTER.toString()).doesNotContain(MATCH.toString());
        assertThat(first.toString()).doesNotContain("signed.jwt").contains("REDACTED");
    }

    @Test
    void rotatesTheRoomAndIdentityWhenTheOpponentParticipantCycleChanges() {
        MediaTokenService service = service(enabledProperties(), 10);
        service.issue(MATCH, REQUESTER);
        when(matches.mediaContext(MATCH, REQUESTER.toString())).thenReturn(
                new MatchApplicationService.MediaMatchContext(
                        MATCH, OPPONENT.toString(), PARTICIPANT_CYCLE.plusSeconds(1), false, null));
        service.issue(MATCH, REQUESTER);

        ArgumentCaptor<String> room = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> identity = ArgumentCaptor.forClass(String.class);
        verify(issuer, org.mockito.Mockito.times(2)).issue(
                room.capture(), identity.capture(), org.mockito.ArgumentMatchers.eq("Player One"),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        assertThat(room.getAllValues()).doesNotHaveDuplicates();
        assertThat(identity.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void deniesDisabledUnverifiedSuspendedBlockedMissingOpponentAndExpiredMatches() {
        MediaTokenService disabled = service(new MediaProperties(
                false, "", "", "", Duration.ofMinutes(5), Duration.ofMinutes(10)), 10);
        assertCode(() -> disabled.issue(MATCH, REQUESTER), "MEDIA_DISABLED");
        verify(issuer, never()).issue(any(), any(), any(), any());

        when(accounts.findById(REQUESTER)).thenReturn(Optional.of(account(AccountStatus.UNVERIFIED)));
        assertCode(() -> service(enabledProperties(), 10).issue(MATCH, REQUESTER), "MEDIA_NOT_ALLOWED");
        when(accounts.findById(REQUESTER)).thenReturn(Optional.of(account(AccountStatus.SUSPENDED)));
        assertCode(() -> service(enabledProperties(), 10).issue(MATCH, REQUESTER), "MEDIA_NOT_ALLOWED");
        when(accounts.findById(REQUESTER)).thenReturn(Optional.of(account(AccountStatus.DELETED)));
        assertCode(() -> service(enabledProperties(), 10).issue(MATCH, REQUESTER), "MEDIA_NOT_ALLOWED");

        when(accounts.findById(REQUESTER)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(blocks.existsEitherDirection(REQUESTER, OPPONENT)).thenReturn(true);
        assertCode(() -> service(enabledProperties(), 10).issue(MATCH, REQUESTER), "MEDIA_BLOCKED");
        when(blocks.existsEitherDirection(REQUESTER, OPPONENT)).thenReturn(false);
        when(matches.mediaContext(MATCH, REQUESTER.toString())).thenReturn(
                new MatchApplicationService.MediaMatchContext(MATCH, null, null, false, null));
        assertCode(() -> service(enabledProperties(), 10).issue(MATCH, REQUESTER), "MEDIA_OPPONENT_UNAVAILABLE");
        when(matches.mediaContext(MATCH, REQUESTER.toString())).thenReturn(
                new MatchApplicationService.MediaMatchContext(
                        MATCH, OPPONENT.toString(), PARTICIPANT_CYCLE, true,
                        NOW.minus(Duration.ofMinutes(10)).minusMillis(1)));
        assertCode(() -> service(enabledProperties(), 10).issue(MATCH, REQUESTER), "MEDIA_MATCH_ENDED");
    }

    @Test
    void allowsTheTenMinutePostMatchWindowAndRateLimitsTokenMinting() {
        when(matches.mediaContext(MATCH, REQUESTER.toString())).thenReturn(
                new MatchApplicationService.MediaMatchContext(
                        MATCH, OPPONENT.toString(), PARTICIPANT_CYCLE, true,
                        NOW.minus(Duration.ofMinutes(10))));
        MediaTokenService onePermit = service(enabledProperties(), 1);
        assertThat(onePermit.issue(MATCH, REQUESTER).token()).isEqualTo("signed.jwt");
        assertCode(() -> onePermit.issue(MATCH, REQUESTER), "MEDIA_RATE_LIMITED");
    }

    private MediaTokenService service(MediaProperties properties, int maximum) {
        return new MediaTokenService(
                properties, matches, accounts, blocks,
                new MediaTokenRateLimiter(clock, maximum, Duration.ofMinutes(1)), issuer, clock);
    }

    private static MediaProperties enabledProperties() {
        return new MediaProperties(
                true, "wss://example.livekit.cloud", "api-key", "a-long-test-secret-value",
                Duration.ofMinutes(5), Duration.ofMinutes(10));
    }

    private static UserAccountEntity account(AccountStatus status) {
        return new UserAccountEntity(
                REQUESTER, "player_one", "player_one", "one@example.com", "one@example.com",
                "encoded", "Player One", status, NOW);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(MediaException.class)
                .extracting(exception -> ((MediaException) exception).code())
                .isEqualTo(code);
    }
}
