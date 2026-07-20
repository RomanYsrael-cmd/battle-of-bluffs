package com.romanysrael.battleofbluffs.media;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.romanysrael.battleofbluffs.game.web.DevelopmentApiExceptionHandler;
import com.romanysrael.battleofbluffs.shared.config.SecurityConfig;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaController.class)
@Import({DevelopmentApiExceptionHandler.class, SecurityConfig.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class MediaControllerTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID MATCH_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final AccountPrincipal USER = new AccountPrincipal(
            USER_ID, "player", "Player", AccountStatus.ACTIVE, "encoded");

    @Autowired MockMvc mvc;
    @MockitoBean MediaTokenService media;
    @MockitoBean AccountUserDetailsService userDetails;

    @BeforeEach
    void activeAccount() {
        when(userDetails.loadUserByUsername(USER.username())).thenReturn(USER);
    }

    @Test
    void requiresAuthenticationAndCsrfThenReturnsASecretMinimalResponse() throws Exception {
        when(media.issue(MATCH_ID, USER_ID)).thenReturn(
                new MediaTokenService.MediaTokenResponse(
                        true, "wss://example.livekit.cloud", "secret-token",
                        java.time.Instant.parse("2026-07-20T12:05:00Z"),
                        new MediaTokenService.MediaRoomInfo(MATCH_ID, 2)));

        mvc.perform(post("/api/matches/{id}/media-token", MATCH_ID).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/matches/{id}/media-token", MATCH_ID).with(user(USER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/matches/{id}/media-token", MATCH_ID).with(user(USER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.url").value("wss://example.livekit.cloud"))
                .andExpect(jsonPath("$.token").value("secret-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-20T12:05:00Z"))
                .andExpect(jsonPath("$.room.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.room.participantCountLimit").value(2))
                .andExpect(jsonPath("$.room.name").doesNotExist())
                .andExpect(jsonPath("$.identity").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.apiSecret").doesNotExist());
    }

    @Test
    void mapsDisabledAndRateLimitedFailuresWithoutLeakingConfiguration() throws Exception {
        when(media.issue(MATCH_ID, USER_ID)).thenThrow(
                new MediaException("MEDIA_DISABLED", "Optional audio and video is not available."));
        mvc.perform(post("/api/matches/{id}/media-token", MATCH_ID).with(user(USER)).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_DISABLED"))
                .andExpect(jsonPath("$.context").doesNotExist());
    }
}
