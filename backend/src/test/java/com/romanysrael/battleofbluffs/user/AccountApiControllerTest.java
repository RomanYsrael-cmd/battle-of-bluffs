package com.romanysrael.battleofbluffs.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

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
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")))
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

    @Test
    void invalidLoginUsesOneGenericUnauthorizedResponse() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"marshal\",\"password\":\"WrongPassword!2026\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username/email or password."));
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOriginWithCredentials() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void hostileHostHeaderIsRejectedBeforeRequestProcessing() throws Exception {
        mvc.perform(get("/api/auth/csrf").header("Host", "attacker.example"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HOST"));
    }

    @Test
    void rateLimitIdentityUsesTheSocketPeerAndIgnoresForgedForwardedHeaders() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .with(request -> { request.setRemoteAddr("192.0.2.8"); return request; })
                        .header("X-Forwarded-For", "203.0.113.99")
                        .header("Forwarded", "for=203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson()))
                .andExpect(status().isCreated());

        verify(rateLimiter).requireRegistration("ip:192.0.2.8");
    }

    @Test
    void logoutInvalidatesTheAuthenticatedServerSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"marshal\",\"password\":\"Strategist!2026\"}"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void credentialAndTokenDtosNeverRenderSecretsIntoLogs() {
        assertThat(new AccountApiController.LoginRequest("marshal", "Strategist!2026").toString())
                .doesNotContain("marshal", "Strategist!2026");
        assertThat(new AccountApiController.TokenRequest("verification-secret").toString())
                .doesNotContain("verification-secret");
        assertThat(new AccountApiController.ResetPasswordRequest(
                "reset-secret", "Replacement!2026").toString())
                .doesNotContain("reset-secret", "Replacement!2026");
        assertThat(new AccountApiController.CsrfView("X-CSRF-TOKEN", "csrf-secret").toString())
                .doesNotContain("csrf-secret");
    }

    @Test
    void suspendedAuthenticatedSessionIsInvalidatedBeforeApiAccess() throws Exception {
        AccountPrincipal suspended = new AccountPrincipal(
                USER_ID, "marshal", "Marshal", AccountStatus.SUSPENDED, "encoded");

        mvc.perform(get("/api/auth/me")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(suspended)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
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
