package com.romanysrael.battleofbluffs.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccountEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "normalized_username", nullable = false, unique = true, length = 32)
    private String normalizedUsername;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, unique = true, length = 320)
    private String normalizedEmail;

    @Column(name = "encoded_password", nullable = false)
    private String encodedPassword;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 24)
    private AccountStatus accountStatus;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(
            UUID id,
            String username,
            String normalizedUsername,
            String email,
            String normalizedEmail,
            String encodedPassword,
            String displayName,
            AccountStatus accountStatus,
            Instant now) {
        this.id = id;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.encodedPassword = encodedPassword;
        this.displayName = displayName;
        this.accountStatus = accountStatus;
        this.emailVerified = accountStatus == AccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getEncodedPassword() {
        return encodedPassword;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void verifyEmail(Instant now) {
        emailVerified = true;
        if (accountStatus == AccountStatus.UNVERIFIED) {
            accountStatus = AccountStatus.ACTIVE;
        }
        updatedAt = now;
    }

    public void changePassword(String encodedPassword, Instant now) {
        this.encodedPassword = encodedPassword;
        updatedAt = now;
    }

    public void recordLogin(Instant now) {
        lastLoginAt = now;
        updatedAt = now;
    }

    public void updateDisplayName(String displayName, Instant now) {
        this.displayName = displayName;
        this.updatedAt = now;
    }
}
