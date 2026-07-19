package com.romanysrael.battleofbluffs.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.romanysrael.battleofbluffs.competition.CompetitiveTier;
import com.romanysrael.battleofbluffs.competition.RatingService;
import com.romanysrael.battleofbluffs.game.web.DevelopmentApiExceptionHandler;
import com.romanysrael.battleofbluffs.shared.config.SecurityConfig;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, DevelopmentApiExceptionHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class ProfileControllerTest {
    private static final AccountPrincipal PLAYER = new AccountPrincipal(
            UUID.randomUUID(), "marshal", "Marshal", AccountStatus.ACTIVE, "{noop}unused");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProfileService profiles;

    @MockitoBean
    private AccountUserDetailsService userDetailsService;

    @Test
    void privateProfileRequiresAuthenticationAndUpdateRequiresCsrf() throws Exception {
        mvc.perform(get("/api/profile/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/profile/me")
                        .with(user(PLAYER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Field Marshal\"}"))
                .andExpect(status().isForbidden());

        ProfileService.PrivateProfileView updated = privateProfile("Field Marshal");
        when(profiles.update(PLAYER.userId(), "Field Marshal")).thenReturn(updated);
        mvc.perform(patch("/api/profile/me")
                        .with(user(PLAYER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Field Marshal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Field Marshal"))
                .andExpect(jsonPath("$.email").value("marshal@example.test"));
        verify(profiles).update(PLAYER.userId(), "Field Marshal");
    }

    @Test
    void publicProfileResponseCannotSerializePrivateEmail() throws Exception {
        ProfileService.PrivateProfileView privateProfile = privateProfile("Marshal");
        ProfileService.PublicProfileView publicProfile = new ProfileService.PublicProfileView(
                privateProfile.username(),
                privateProfile.displayName(),
                privateProfile.joinedAt(),
                privateProfile.statistics(),
                privateProfile.rating(),
                List.of());
        when(profiles.publicProfile("marshal")).thenReturn(publicProfile);

        mvc.perform(get("/api/profiles/marshal").with(user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("marshal"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.emailVerified").doesNotExist());
    }

    private static ProfileService.PrivateProfileView privateProfile(String displayName) {
        return new ProfileService.PrivateProfileView(
                "marshal",
                displayName,
                "marshal@example.test",
                Instant.parse("2026-01-01T00:00:00Z"),
                true,
                new ProfileService.ProfileStats(2, 1, 1, 1, 1, 0, 0, 0.5),
                new RatingService.CurrentRating(
                        1220,
                        CompetitiveTier.SERGEANT,
                        "Sergeant",
                        1,
                        1,
                        0,
                        0,
                        true,
                        UUID.randomUUID(),
                        "Season One"),
                List.of());
    }
}
