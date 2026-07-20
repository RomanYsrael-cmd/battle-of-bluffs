package com.romanysrael.battleofbluffs.media;

import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.social.BlockRelationshipService;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.UserAccountEntity;
import com.romanysrael.battleofbluffs.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaTokenService {
    private final MediaProperties properties;
    private final MatchApplicationService matches;
    private final UserAccountRepository accounts;
    private final BlockRelationshipService blocks;
    private final MediaTokenRateLimiter rateLimiter;
    private final MediaTokenIssuer issuer;
    private final Clock clock;

    MediaTokenService(
            MediaProperties properties,
            MatchApplicationService matches,
            UserAccountRepository accounts,
            BlockRelationshipService blocks,
            MediaTokenRateLimiter rateLimiter,
            MediaTokenIssuer issuer,
            Clock clock) {
        this.properties = properties;
        this.matches = matches;
        this.accounts = accounts;
        this.blocks = blocks;
        this.rateLimiter = rateLimiter;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MediaTokenResponse issue(UUID matchId, UUID requesterId) {
        if (!properties.enabled()) {
            throw new MediaException("MEDIA_DISABLED", "Optional audio and video is not available.");
        }
        rateLimiter.requirePermit(requesterId.toString());
        UserAccountEntity account = accounts.findById(requesterId)
                .orElseThrow(() -> denied("Account is not eligible for media."));
        if (account.getAccountStatus() != AccountStatus.ACTIVE || !account.isEmailVerified()) {
            throw denied("A verified active account is required for media.");
        }
        MatchApplicationService.MediaMatchContext context =
                matches.mediaContext(matchId, requesterId.toString());
        if (context.opponentId() == null) {
            throw new MediaException("MEDIA_OPPONENT_UNAVAILABLE", "Media is available after an opponent joins.");
        }
        if (context.participantCycleStartedAt() == null) {
            throw denied("Match participants are not eligible for media.");
        }
        UUID opponentId;
        try {
            opponentId = UUID.fromString(context.opponentId());
        } catch (IllegalArgumentException exception) {
            throw denied("Match participants are not eligible for media.");
        }
        if (blocks.existsEitherDirection(requesterId, opponentId)) {
            throw new MediaException("MEDIA_BLOCKED", "A block relationship prevents media access.");
        }
        if (context.terminal() && (context.terminalAt() == null
                || clock.instant().isAfter(context.terminalAt().plus(properties.postMatchWindow())))) {
            throw new MediaException("MEDIA_MATCH_ENDED", "The post-match media window has ended.");
        }
        String cycle = context.participantCycleStartedAt().toString();
        String roomName = "gotg-match-" + opaque("room:" + matchId + ':' + cycle);
        String identity = "gotg-player-" + opaque("identity:" + matchId + ':' + cycle + ':' + requesterId);
        String token = issuer.issue(roomName, identity, account.getDisplayName(), properties.tokenTtl());
        return new MediaTokenResponse(
                true,
                properties.url(),
                token,
                clock.instant().plus(properties.tokenTtl()),
                new MediaRoomInfo(matchId, 2));
    }

    private String opaque(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 24);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static MediaException denied(String message) {
        return new MediaException("MEDIA_NOT_ALLOWED", message);
    }

    public record MediaTokenResponse(
            boolean enabled,
            String url,
            String token,
            java.time.Instant expiresAt,
            MediaRoomInfo room) {
        @Override public String toString() {
            return "MediaTokenResponse[enabled=" + enabled + ", url=" + url
                    + ", token=REDACTED, expiresAt=" + expiresAt + ", room=" + room + ']';
        }
    }

    public record MediaRoomInfo(UUID matchId, int participantCountLimit) { }
}
