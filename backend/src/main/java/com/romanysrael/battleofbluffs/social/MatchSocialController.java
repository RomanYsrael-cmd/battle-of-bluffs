package com.romanysrael.battleofbluffs.social;

import com.romanysrael.battleofbluffs.social.MatchChatService.ChatMessageView;
import com.romanysrael.battleofbluffs.social.MatchModerationService.ModerationStatus;
import com.romanysrael.battleofbluffs.social.MatchModerationService.ReportReceipt;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches/{matchId}")
public class MatchSocialController {
    private final MatchChatService chat;
    private final MatchModerationService moderation;

    public MatchSocialController(MatchChatService chat, MatchModerationService moderation) {
        this.chat = chat;
        this.moderation = moderation;
    }

    @GetMapping("/chat")
    List<ChatMessageView> history(
            @PathVariable UUID matchId,
            @RequestParam(required = false) Long afterSequence,
            Authentication authentication) {
        return chat.history(matchId, principal(authentication).userId(), afterSequence);
    }

    @GetMapping("/moderation")
    ModerationStatus moderationStatus(
            @PathVariable UUID matchId,
            Authentication authentication) {
        return moderation.status(matchId, principal(authentication).userId());
    }

    @PostMapping("/block")
    ModerationStatus block(
            @PathVariable UUID matchId,
            Authentication authentication) {
        return moderation.block(matchId, principal(authentication).userId());
    }

    @DeleteMapping("/block")
    ModerationStatus unblock(
            @PathVariable UUID matchId,
            Authentication authentication) {
        return moderation.unblock(matchId, principal(authentication).userId());
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ReportReceipt report(
            @PathVariable UUID matchId,
            @Valid @RequestBody ReportRequest request,
            Authentication authentication) {
        return moderation.report(
                matchId,
                principal(authentication).userId(),
                request.category(),
                request.comment(),
                request.chatMessageReferences());
    }

    private AccountPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return principal;
    }

    public record ReportRequest(
            @NotNull ReportCategory category,
            @Size(max = 1000) String comment,
            @Size(max = 20) List<@NotNull UUID> chatMessageReferences) {
        @Override public String toString() { return "ReportRequest[category=" + category + ", content=REDACTED]"; }
    }
}
