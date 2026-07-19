package com.romanysrael.battleofbluffs.shared.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
final class ProductionConfigurationValidator {
    private final String frontendUrl;
    private final String mailFrom;
    private final boolean secureCookie;
    private final boolean smtpTlsRequired;
    private final Environment environment;

    ProductionConfigurationValidator(
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.mail.from}") String mailFrom,
            @Value("${server.servlet.session.cookie.secure}") boolean secureCookie,
            @Value("${spring.mail.properties.mail.smtp.starttls.required}") boolean smtpTlsRequired,
            Environment environment) {
        this.frontendUrl = frontendUrl;
        this.mailFrom = mailFrom;
        this.secureCookie = secureCookie;
        this.smtpTlsRequired = smtpTlsRequired;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            throw new IllegalStateException("Development and test profiles cannot be combined with prod");
        }
        rejectDiagnosticMode("debug");
        rejectDiagnosticMode("trace");
        URI publicUri;
        try {
            publicUri = URI.create(frontendUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("FRONTEND_URL must be an absolute trusted HTTPS origin", exception);
        }
        if (!"https".equalsIgnoreCase(publicUri.getScheme())
                || publicUri.getHost() == null
                || publicUri.getRawUserInfo() != null
                || publicUri.getRawQuery() != null
                || publicUri.getRawFragment() != null) {
            throw new IllegalStateException("FRONTEND_URL must be an absolute trusted HTTPS origin");
        }
        if (!secureCookie) {
            throw new IllegalStateException("Production session cookies must be Secure");
        }
        if (!smtpTlsRequired) {
            throw new IllegalStateException("Production SMTP must require STARTTLS");
        }
        if (mailFrom.isBlank() || mailFrom.indexOf('\r') >= 0 || mailFrom.indexOf('\n') >= 0) {
            throw new IllegalStateException("MAIL_FROM must be a safe non-empty address");
        }
    }

    private void rejectDiagnosticMode(String property) {
        String value = environment.getProperty(property);
        if (value != null && !value.equalsIgnoreCase("false")) {
            throw new IllegalStateException(
                    "Production diagnostic mode is disabled; remove " + property.toUpperCase() + " from the environment");
        }
    }
}
