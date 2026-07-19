package com.romanysrael.battleofbluffs.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romanysrael.battleofbluffs.user.AccountService.RegisterAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T01:00:00Z");
    private static final String GOOD_PASSWORD = "Strategist!2026";

    private UserAccountRepository accounts;
    private EmailVerificationTokenRepository verificationTokens;
    private PasswordResetTokenRepository resetTokens;
    private PasswordEncoder passwordEncoder;
    private SessionRegistry sessionRegistry;
    private RecordingMailSender mailSender;
    private SecureAccountTokenGenerator tokenGenerator;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        verificationTokens = mock(EmailVerificationTokenRepository.class);
        resetTokens = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        sessionRegistry = mock(SessionRegistry.class);
        mailSender = new RecordingMailSender();
        tokenGenerator = new SecureAccountTokenGenerator();
        service = new AccountService(
                accounts,
                verificationTokens,
                resetTokens,
                passwordEncoder,
                tokenGenerator,
                mailSender,
                sessionRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "http://localhost:5173/");
    }

    @Test
    void registrationNormalizesUniquenessEncodesPasswordAndStoresOnlyVerificationHash() {
        when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("{bcrypt}encoded");
        when(accounts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.register(new RegisterAccount(
                " FieldMarshal ", " General@Example.COM ", GOOD_PASSWORD, " The General "));

        ArgumentCaptor<UserAccountEntity> accountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(accounts).saveAndFlush(accountCaptor.capture());
        UserAccountEntity account = accountCaptor.getValue();
        assertThat(account.getNormalizedUsername()).isEqualTo("fieldmarshal");
        assertThat(account.getNormalizedEmail()).isEqualTo("general@example.com");
        assertThat(account.getEncodedPassword()).isEqualTo("{bcrypt}encoded");
        assertThat(view.status()).isEqualTo(AccountStatus.UNVERIFIED);

        ArgumentCaptor<EmailVerificationTokenEntity> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        verify(verificationTokens).save(tokenCaptor.capture());
        String rawToken = mailSender.verificationUrl.substring(mailSender.verificationUrl.indexOf("token=") + 6);
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(tokenGenerator.hash(rawToken));
        assertThat(tokenCaptor.getValue().getTokenHash()).doesNotContain(rawToken);
        assertThat(mailSender.verificationUrl).startsWith("http://localhost:5173/verify-email?token=");
    }

    @Test
    void registrationRejectsWeakPasswordsBeforeWritingAnything() {
        assertThatThrownBy(() -> service.register(new RegisterAccount(
                "marshal", "marshal@example.com", "alllowercase", "Marshal")))
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo("INVALID_ACCOUNT_INPUT");

        verify(accounts, never()).save(any());
        assertThat(mailSender.verificationUrl).isNull();
    }

    @Test
    void verificationTokenIsOneTimeAndActivatesAccount() {
        String rawToken = "verification-secret";
        UserAccountEntity account = account(AccountStatus.UNVERIFIED);
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity(
                UUID.randomUUID(), account.getId(), tokenGenerator.hash(rawToken), NOW.plusSeconds(60), NOW);
        when(verificationTokens.findByTokenHash(tokenGenerator.hash(rawToken))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        assertThat(service.verifyEmail(rawToken).emailVerified()).isTrue();
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.verifyEmail(rawToken))
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void expiredVerificationAndPasswordResetTokensAreRejectedWithoutMutatingAccounts() {
        UserAccountEntity account = account(AccountStatus.UNVERIFIED);
        String verificationSecret = "expired-verification";
        String resetSecret = "expired-reset";
        EmailVerificationTokenEntity verification = new EmailVerificationTokenEntity(
                UUID.randomUUID(), account.getId(), tokenGenerator.hash(verificationSecret),
                NOW.minusSeconds(1), NOW.minusSeconds(120));
        PasswordResetTokenEntity reset = new PasswordResetTokenEntity(
                UUID.randomUUID(), account.getId(), tokenGenerator.hash(resetSecret),
                NOW.minusSeconds(1), NOW.minusSeconds(120));
        when(verificationTokens.findByTokenHash(tokenGenerator.hash(verificationSecret)))
                .thenReturn(Optional.of(verification));
        when(resetTokens.findByTokenHash(tokenGenerator.hash(resetSecret)))
                .thenReturn(Optional.of(reset));

        assertThatThrownBy(() -> service.verifyEmail(verificationSecret))
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo("INVALID_TOKEN");
        assertThatThrownBy(() -> service.resetPassword(resetSecret, "Replacement!2026"))
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo("INVALID_TOKEN");
        assertThat(account.isEmailVerified()).isFalse();
        verify(passwordEncoder, never()).encode("Replacement!2026");
    }

    @Test
    void passwordResetIsOneTimeAndExpiresEveryActiveSessionForTheAccount() {
        String rawToken = "password-reset-secret";
        UserAccountEntity account = account(AccountStatus.ACTIVE);
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                UUID.randomUUID(), account.getId(), tokenGenerator.hash(rawToken), NOW.plusSeconds(60), NOW);
        AccountPrincipal principal = AccountPrincipal.from(account);
        SessionInformation session = mock(SessionInformation.class);
        when(resetTokens.findByTokenHash(tokenGenerator.hash(rawToken))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("Replacement!2026")).thenReturn("{bcrypt}replacement");
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(session));

        service.resetPassword(rawToken, "Replacement!2026");

        assertThat(account.getEncodedPassword()).isEqualTo("{bcrypt}replacement");
        verify(session).expireNow();
        assertThatThrownBy(() -> service.resetPassword(rawToken, "Replacement!2026"))
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void unknownForgotPasswordRequestHasNoObservableMailSideEffect() {
        when(accounts.findByNormalizedEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset(" Nobody@Example.com ");

        assertThat(mailSender.resetUrl).isNull();
        verify(resetTokens, never()).save(any());
    }

    private static UserAccountEntity account(AccountStatus status) {
        return new UserAccountEntity(
                UUID.randomUUID(),
                "marshal",
                "marshal",
                "marshal@example.com",
                "marshal@example.com",
                "{bcrypt}old",
                "Marshal",
                status,
                NOW);
    }

    private static final class RecordingMailSender implements AccountMailSender {
        private String verificationUrl;
        private String resetUrl;

        @Override
        public void sendVerification(String recipient, String displayName, String verificationUrl) {
            this.verificationUrl = verificationUrl;
        }

        @Override
        public void sendPasswordReset(String recipient, String displayName, String resetUrl) {
            this.resetUrl = resetUrl;
        }
    }
}
