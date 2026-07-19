package com.romanysrael.battleofbluffs.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public final class SmtpAccountMailSender implements AccountMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpAccountMailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerification(String recipient, String displayName, String verificationUrl) {
        send(
                recipient,
                "Verify your Games of the Generals account",
                "Hello " + displayName + ",\n\nVerify your email address:\n" + verificationUrl
                        + "\n\nThis link expires in 24 hours.");
    }

    @Override
    public void sendPasswordReset(String recipient, String displayName, String resetUrl) {
        send(
                recipient,
                "Reset your Games of the Generals password",
                "Hello " + displayName + ",\n\nReset your password:\n" + resetUrl
                        + "\n\nThis link expires in one hour. Ignore this message if you did not request it.");
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
