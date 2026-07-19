package com.romanysrael.battleofbluffs.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,32}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_LIFETIME = Duration.ofHours(1);

    private final UserAccountRepository accounts;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureAccountTokenGenerator tokenGenerator;
    private final AccountMailSender mailSender;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;
    private final String frontendUrl;

    public AccountService(
            UserAccountRepository accounts,
            EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder,
            SecureAccountTokenGenerator tokenGenerator,
            AccountMailSender mailSender,
            SessionRegistry sessionRegistry,
            Clock clock,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.accounts = accounts;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.mailSender = mailSender;
        this.sessionRegistry = sessionRegistry;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public AccountView register(RegisterAccount command) {
        String username = canonical(command.username()).strip();
        String email = canonical(command.email()).strip();
        String displayName = Normalizer.normalize(command.displayName(), Normalizer.Form.NFC).strip();
        validateUsername(username);
        validateEmail(email);
        validateDisplayName(displayName);
        validatePassword(command.password());

        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);
        if (accounts.existsByNormalizedUsernameOrNormalizedEmail(normalizedUsername, normalizedEmail)) {
            throw accountConflict();
        }

        Instant now = clock.instant();
        UserAccountEntity account = new UserAccountEntity(
                UUID.randomUUID(),
                username,
                normalizedUsername,
                email,
                normalizedEmail,
                passwordEncoder.encode(command.password()),
                displayName,
                AccountStatus.UNVERIFIED,
                now);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw accountConflict();
        }
        issueVerification(account, now);
        return AccountView.from(account);
    }

    @Transactional
    public void resendVerification(UUID userId) {
        UserAccountEntity account = requireAccount(userId);
        if (account.isEmailVerified()) {
            return;
        }
        issueVerification(account, clock.instant());
    }

    @Transactional
    public AccountView verifyEmail(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationTokenEntity token = verificationTokens.findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(AccountService::invalidToken);
        requireUsable(token.getUsedAt(), token.getExpiresAt(), now);
        token.use(now);
        UserAccountEntity account = requireAccount(token.getUserId());
        account.verifyEmail(now);
        verificationTokens.invalidateUnused(account.getId(), now);
        return AccountView.from(account);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = normalize(email);
        accounts.findByNormalizedEmail(normalizedEmail).ifPresent(account -> {
            Instant now = clock.instant();
            resetTokens.invalidateUnused(account.getId(), now);
            String rawToken = tokenGenerator.generate();
            resetTokens.save(new PasswordResetTokenEntity(
                    UUID.randomUUID(),
                    account.getId(),
                    tokenGenerator.hash(rawToken),
                    now.plus(RESET_LIFETIME),
                    now));
            mailSender.sendPasswordReset(
                    account.getEmail(),
                    account.getDisplayName(),
                    frontendUrl + "/reset-password?token=" + rawToken);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        Instant now = clock.instant();
        PasswordResetTokenEntity token = resetTokens.findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(AccountService::invalidToken);
        requireUsable(token.getUsedAt(), token.getExpiresAt(), now);
        token.use(now);
        UserAccountEntity account = requireAccount(token.getUserId());
        account.changePassword(passwordEncoder.encode(newPassword), now);
        resetTokens.invalidateUnused(account.getId(), now);
        expireSessions(account.getId());
    }

    @Transactional
    public AccountView recordLogin(UUID userId) {
        UserAccountEntity account = requireAccount(userId);
        account.recordLogin(clock.instant());
        return AccountView.from(account);
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID userId) {
        return AccountView.from(requireAccount(userId));
    }

    @Transactional
    public AccountView updateDisplayName(UUID userId, String submittedDisplayName) {
        String displayName = submittedDisplayName == null ? "" : submittedDisplayName.strip();
        validateDisplayName(displayName);
        UserAccountEntity account = requireAccount(userId);
        account.updateDisplayName(displayName, clock.instant());
        return AccountView.from(account);
    }

    private void issueVerification(UserAccountEntity account, Instant now) {
        verificationTokens.invalidateUnused(account.getId(), now);
        String rawToken = tokenGenerator.generate();
        verificationTokens.save(new EmailVerificationTokenEntity(
                UUID.randomUUID(),
                account.getId(),
                tokenGenerator.hash(rawToken),
                now.plus(VERIFICATION_LIFETIME),
                now));
        mailSender.sendVerification(
                account.getEmail(),
                account.getDisplayName(),
                frontendUrl + "/verify-email?token=" + rawToken);
    }

    private void expireSessions(UUID userId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AccountPrincipal accountPrincipal && accountPrincipal.userId().equals(userId)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }

    private UserAccountEntity requireAccount(UUID userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> new AccountException("ACCOUNT_NOT_FOUND", "Account not found."));
    }

    private static void validateUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw validation("Username must be 3–32 letters, numbers, or underscores.");
        }
    }

    private static void validateEmail(String email) {
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw validation("Enter a valid email address.");
        }
    }

    private static void validateDisplayName(String displayName) {
        if (displayName.length() < 2 || displayName.length() > 50
                || CONTROL_CHARACTER.matcher(displayName).find()) {
            throw validation("Display name must contain 2–50 visible characters.");
        }
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 72
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw validation("Password must be 12–72 characters with upper, lower, number, and symbol.");
        }
    }

    private static String normalize(String value) {
        return canonical(value).strip().toLowerCase(Locale.ROOT);
    }

    private static String canonical(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    private static void requireUsable(Instant usedAt, Instant expiresAt, Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt)) {
            throw invalidToken();
        }
    }

    private static AccountException accountConflict() {
        return new AccountException("ACCOUNT_CONFLICT", "An account with those details already exists.");
    }

    private static AccountException invalidToken() {
        return new AccountException("INVALID_TOKEN", "This link is invalid or has expired.");
    }

    private static AccountException validation(String message) {
        return new AccountException("INVALID_ACCOUNT_INPUT", message);
    }

    public record RegisterAccount(String username, String email, String password, String displayName) {
    }

    public record AccountView(
            UUID id,
            String username,
            String displayName,
            AccountStatus status,
            boolean emailVerified) {
        static AccountView from(UserAccountEntity account) {
            return new AccountView(
                    account.getId(),
                    account.getUsername(),
                    account.getDisplayName(),
                    account.getAccountStatus(),
                    account.isEmailVerified());
        }
    }
}
