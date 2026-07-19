package com.romanysrael.battleofbluffs.matchmaking;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.romanysrael.battleofbluffs.game.web.DevelopmentApiExceptionHandler;
import com.romanysrael.battleofbluffs.matchmaking.RankedMatchmakingService.QueueStatus;
import com.romanysrael.battleofbluffs.shared.config.SecurityConfig;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RankedMatchmakingController.class)
@Import({SecurityConfig.class, DevelopmentApiExceptionHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class RankedMatchmakingControllerTest {
    private static final AccountPrincipal PLAYER = new AccountPrincipal(
            UUID.randomUUID(), "marshal", "Marshal", AccountStatus.ACTIVE, "{noop}unused");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RankedMatchmakingService matchmaking;

    @MockitoBean
    private AccountUserDetailsService userDetailsService;

    @Test
    void queueEndpointsRequireAuthenticationAndStateChangesRequireCsrf() throws Exception {
        mvc.perform(get("/api/matchmaking/status"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/matchmaking/ranked").with(user(PLAYER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/matchmaking/ranked").with(user(PLAYER)))
                .andExpect(status().isForbidden());

        QueueStatus queued = new QueueStatus(
                "QUEUED", Instant.parse("2026-07-19T06:00:00Z"), 0, 200, 1200, null);
        when(matchmaking.enqueue(PLAYER.userId())).thenReturn(queued);
        mvc.perform(post("/api/matchmaking/ranked").with(user(PLAYER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.searchRange").value(200));
        verify(matchmaking).enqueue(PLAYER.userId());
    }

    @Test
    void verificationAndActiveMatchFailuresUseStructuredAuthorizationAndConflictStatuses()
            throws Exception {
        when(matchmaking.enqueue(PLAYER.userId())).thenThrow(new MatchmakingException(
                "EMAIL_VERIFICATION_REQUIRED", "Verify your email."));
        mvc.perform(post("/api/matchmaking/ranked").with(user(PLAYER)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));

        org.mockito.Mockito.reset(matchmaking);
        when(matchmaking.enqueue(PLAYER.userId())).thenThrow(new MatchmakingException(
                "ACTIVE_MATCH", "Finish the current match."));
        mvc.perform(post("/api/matchmaking/ranked").with(user(PLAYER)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_MATCH"));
    }
}
