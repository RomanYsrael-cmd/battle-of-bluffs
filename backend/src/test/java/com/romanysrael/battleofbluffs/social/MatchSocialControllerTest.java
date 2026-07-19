package com.romanysrael.battleofbluffs.social;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.romanysrael.battleofbluffs.game.web.DevelopmentApiExceptionHandler;
import com.romanysrael.battleofbluffs.shared.config.SecurityConfig;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountStatus;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(MatchSocialController.class)
@Import({SecurityConfig.class, DevelopmentApiExceptionHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class MatchSocialControllerTest {
    private static final UUID MATCH_ID = UUID.randomUUID();
    private static final AccountPrincipal PLAYER = new AccountPrincipal(
            UUID.randomUUID(), "player", "Player", AccountStatus.ACTIVE, "{noop}unused");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchChatService chat;

    @MockitoBean
    private MatchModerationService moderation;

    @MockitoBean
    private AccountUserDetailsService userDetailsService;

    @BeforeEach
    void accountRemainsActive() {
        when(userDetailsService.loadUserByUsername(PLAYER.username())).thenReturn(PLAYER);
    }

    @Test
    void anonymousUsersCannotReadPrivateChatHistory() throws Exception {
        mvc.perform(get("/api/matches/{matchId}/chat", MATCH_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedParticipantIdentityIsDerivedForHistory() throws Exception {
        when(chat.history(MATCH_ID, PLAYER.userId(), null)).thenReturn(List.of());

        mvc.perform(get("/api/matches/{matchId}/chat", MATCH_ID).with(user(PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(chat).history(MATCH_ID, PLAYER.userId(), null);
    }

    @Test
    void blockAndReportRequireCsrfAndNeverAcceptAClientSuppliedOpponentIdentity() throws Exception {
        mvc.perform(post("/api/matches/{matchId}/block", MATCH_ID).with(user(PLAYER)))
                .andExpect(status().isForbidden());

        when(moderation.block(MATCH_ID, PLAYER.userId()))
                .thenReturn(new MatchModerationService.ModerationStatus("Opponent", true));
        mvc.perform(post("/api/matches/{matchId}/block", MATCH_ID)
                        .with(user(PLAYER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByYou").value(true));

        UUID reportId = UUID.randomUUID();
        when(moderation.report(
                MATCH_ID, PLAYER.userId(), ReportCategory.SPAM, "evidence", List.of()))
                .thenReturn(new MatchModerationService.ReportReceipt(reportId, "Report received."));
        mvc.perform(post("/api/matches/{matchId}/reports", MATCH_ID)
                        .with(user(PLAYER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SPAM\",\"comment\":\"evidence\","
                                + "\"chatMessageReferences\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(reportId.toString()));
    }
}
