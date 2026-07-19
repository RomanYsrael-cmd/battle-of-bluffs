package com.romanysrael.battleofbluffs.competition;

import com.romanysrael.battleofbluffs.competition.LeaderboardService.LeaderboardPage;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {
    private final LeaderboardService leaderboards;

    public LeaderboardController(LeaderboardService leaderboards) {
        this.leaderboards = leaderboards;
    }

    @GetMapping("/seasonal")
    LeaderboardPage seasonal(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {
        return leaderboards.seasonal(principal(authentication).userId(), page, size);
    }

    @GetMapping("/all-time")
    LeaderboardPage allTime(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {
        return leaderboards.allTime(principal(authentication).userId(), page, size);
    }

    private AccountPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return principal;
    }
}
