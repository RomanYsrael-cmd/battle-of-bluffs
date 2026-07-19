package com.romanysrael.battleofbluffs.user;

public interface AccountMailSender {
    void sendVerification(String recipient, String displayName, String verificationUrl);

    void sendPasswordReset(String recipient, String displayName, String resetUrl);
}
