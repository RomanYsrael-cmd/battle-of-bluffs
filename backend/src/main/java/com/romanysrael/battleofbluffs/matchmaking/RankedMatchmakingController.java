package com.romanysrael.battleofbluffs.matchmaking;

import com.romanysrael.battleofbluffs.matchmaking.RankedMatchmakingService.QueueStatus;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matchmaking")
public class RankedMatchmakingController {
    private final RankedMatchmakingService matchmaking;

    public RankedMatchmakingController(RankedMatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
    }

    @PostMapping("/ranked")
    QueueStatus enqueue(Authentication authentication) {
        return matchmaking.enqueue(userId(authentication));
    }

    @DeleteMapping("/ranked")
    QueueStatus cancel(Authentication authentication) {
        return matchmaking.cancel(userId(authentication));
    }

    @GetMapping("/status")
    QueueStatus status(Authentication authentication) {
        return matchmaking.current(userId(authentication));
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return principal.userId();
    }
}
