package com.romanysrael.battleofbluffs.profile;

import com.romanysrael.battleofbluffs.profile.ProfileService.MatchHistoryView;
import com.romanysrael.battleofbluffs.profile.ProfileService.MatchListItem;
import com.romanysrael.battleofbluffs.profile.ProfileService.PageView;
import com.romanysrael.battleofbluffs.profile.ProfileService.PrivateProfileView;
import com.romanysrael.battleofbluffs.profile.ProfileService.PublicProfileView;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile/me")
    PrivateProfileView me(Authentication authentication) {
        return profiles.me(principal(authentication).userId());
    }

    @PatchMapping("/profile/me")
    PrivateProfileView update(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        return profiles.update(principal(authentication).userId(), request.displayName());
    }

    @GetMapping("/profiles/{username}")
    PublicProfileView publicProfile(@PathVariable String username) {
        return profiles.publicProfile(username);
    }

    @GetMapping("/profile/me/matches")
    PageView<MatchListItem> matches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return profiles.recent(principal(authentication).userId(), page, size);
    }

    @GetMapping("/matches/{matchId}/history")
    MatchHistoryView history(
            @PathVariable UUID matchId,
            Authentication authentication) {
        return profiles.history(matchId, principal(authentication).userId());
    }

    private AccountPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return principal;
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 50) String displayName) {
        @Override public String toString() { return "UpdateProfileRequest[REDACTED]"; }
    }
}
