package com.romanysrael.battleofbluffs.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.romanysrael.battleofbluffs.game.web.DevelopmentApiExceptionHandler;
import com.romanysrael.battleofbluffs.shared.config.SecurityConfig;
import com.romanysrael.battleofbluffs.user.AccountService.AccountView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountApiController.class)
@Import({SecurityConfig.class, DevelopmentApiExceptionHandler.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class AccountApiControllerTest {
    private static final UUID USER_ID = UUID.fromString("be1e9e77-1b7d-4bbb-87f2-e34c12bccf77");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private AccountRateLimiter rateLimiter;

    @MockitoBean
    private AccountUserDetailsService userDetailsService;

    @BeforeEach
    void configureAccount() {
        when(userDetailsService.loadUserByUsername("marshal"))
                .thenReturn(new AccountPrincipal(
                        USER_ID,
                        "marshal",
                        "Marshal",
                        AccountStatus.UNVERIFIED,
                        "{noop}Strategist!2026"));
        when(accounts.recordLogin(USER_ID)).thenReturn(unverifiedAccount());
        when(accounts.get(USER_ID)).thenReturn(unverifiedAccount());
        when(accounts.register(any())).thenReturn(unverifiedAccount());
    }

    @Test
    void csrfEndpointIssuesSessionBoundRequestToken() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void stateChangingAuthEndpointRejectsMissingCsrfTokenWithStructuredError() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void loginCreatesAuthenticatedServerSessionThatCanReadCurrentAccount() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"marshal\",\"password\":\"Strategist!2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andReturn()
                .getRequest()
                .getSession(false);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("marshal"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void anonymousCurrentAccountRequestIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private static AccountView unverifiedAccount() {
        return new AccountView(USER_ID, "marshal", "Marshal", AccountStatus.UNVERIFIED, false);
    }

    private static String registrationJson() {
        return """
                {
                  "username": "marshal",
                  "email": "marshal@example.com",
                  "password": "Strategist!2026",
                  "displayName": "Marshal"
                }
                """;
    }
}
