package com.romanysrael.battleofbluffs.user;

import java.util.Locale;
import java.text.Normalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public final class AccountUserDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;

    public AccountUserDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String normalizedLogin = Normalizer.normalize(login, Normalizer.Form.NFKC)
                .strip().toLowerCase(Locale.ROOT);
        UserAccountEntity account = accounts.findByNormalizedUsername(normalizedLogin)
                .or(() -> accounts.findByNormalizedEmail(normalizedLogin))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return AccountPrincipal.from(account);
    }
}
