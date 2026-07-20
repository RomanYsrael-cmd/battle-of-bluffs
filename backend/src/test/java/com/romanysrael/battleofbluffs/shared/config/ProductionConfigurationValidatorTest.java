package com.romanysrael.battleofbluffs.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsAnExplicitHttpsOriginSecureCookieAndRequiredSmtpTls() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                false, "", "", "", environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecurePublicUrlsAndMixedDevelopmentProfiles() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "http://play.example.com", "security@example.com", true, true,
                false, "", "", "", production).validate())
                .isInstanceOf(IllegalStateException.class);

        MockEnvironment mixed = new MockEnvironment();
        mixed.setActiveProfiles("prod", "dev");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                false, "", "", "", mixed).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be combined");
    }

    @Test
    void rejectsUnsafeCookieSmtpAndSenderConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", false, true,
                false, "", "", "", environment).validate())
                .hasMessageContaining("Secure");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, false,
                false, "", "", "", environment).validate())
                .hasMessageContaining("STARTTLS");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com\r\nBcc: attacker@example.com",
                true, true, false, "", "", "", environment).validate())
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    void rejectsEnvironmentDiagnosticFlagsInProduction() {
        MockEnvironment environment = new MockEnvironment().withProperty("debug", "true");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                false, "", "", "", environment).validate())
                .hasMessageContaining("diagnostic mode");
    }

    @Test
    void mediaIsFailClosedAndRequiresSecureCompleteLiveKitConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                true, "", "", "", environment).validate())
                .hasMessageContaining("MEDIA_ENABLED");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                true, "ws://media.example.com", "key", "secret", environment).validate())
                .hasMessageContaining("MEDIA_ENABLED");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                true, "wss://self-hosted.example.com", "key", "secret", environment).validate())
                .hasMessageContaining("MEDIA_ENABLED");
        assertThatCode(() -> new ProductionConfigurationValidator(
                "https://play.example.com", "security@example.com", true, true,
                true, "wss://example.livekit.cloud", "key", "secret", environment).validate())
                .doesNotThrowAnyException();
    }
}
