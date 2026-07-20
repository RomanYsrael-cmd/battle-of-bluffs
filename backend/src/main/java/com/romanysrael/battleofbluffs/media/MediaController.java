package com.romanysrael.battleofbluffs.media;

import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public final class MediaController {
    private final MediaTokenService media;

    public MediaController(MediaTokenService media) {
        this.media = media;
    }

    @PostMapping("/{matchId}/media-token")
    MediaTokenService.MediaTokenResponse token(
            @PathVariable UUID matchId,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return media.issue(matchId, principal.userId());
    }
}
