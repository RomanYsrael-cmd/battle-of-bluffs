package com.romanysrael.battleofbluffs.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LiveKitMediaTokenIssuerTest {
    @Test
    void grantsOnlyRoomJoinSubscribeAndCameraMicrophonePublication() throws Exception {
        MediaProperties properties = new MediaProperties(
                true, "wss://example.livekit.cloud", "test-api-key", "a-long-test-secret-value",
                Duration.ofMinutes(5), Duration.ofMinutes(10));
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        String token = new LiveKitMediaTokenIssuer(
                properties, Clock.fixed(now, ZoneOffset.UTC)).issue(
                "gotg-match-opaque", "gotg-player-opaque", "Player", Duration.ofMinutes(5));

        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload)
                .contains("\"iss\":\"test-api-key\"")
                .contains("\"sub\":\"gotg-player-opaque\"")
                .contains("\"roomJoin\":true")
                .contains("\"room\":\"gotg-match-opaque\"")
                .contains("\"canSubscribe\":true")
                .contains("\"canPublish\":true")
                .contains("\"canPublishData\":false")
                .contains("camera", "microphone")
                .doesNotContain("roomAdmin", "roomRecord", "roomCreate", "screen_share", "egress");
        assertThat(token).hasSizeGreaterThan(100);
        JsonNode claims = JsonMapper.builder().build().readTree(payload);
        assertThat(claims.get("exp").asLong())
                .isEqualTo(now.plus(Duration.ofMinutes(5)).getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                "a-long-test-secret-value".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String[] parts = token.split("\\.");
        assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(
                mac.doFinal((parts[0] + '.' + parts[1]).getBytes(StandardCharsets.US_ASCII)));
        assertThat(properties.toString())
                .contains("apiKey=REDACTED", "apiSecret=REDACTED")
                .doesNotContain("test-api-key", "a-long-test-secret-value");
    }
}
