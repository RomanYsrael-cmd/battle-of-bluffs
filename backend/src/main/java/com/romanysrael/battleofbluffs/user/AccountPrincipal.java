package com.romanysrael.battleofbluffs.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AccountPrincipal(
        UUID userId,
        String username,
        String displayName,
        AccountStatus status,
        String password) implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    static AccountPrincipal from(UserAccountEntity account) {
        return new AccountPrincipal(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getAccountStatus(),
                account.getEncodedPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.UNVERIFIED;
    }
}
