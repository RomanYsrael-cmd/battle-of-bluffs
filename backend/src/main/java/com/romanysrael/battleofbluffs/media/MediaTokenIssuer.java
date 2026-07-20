package com.romanysrael.battleofbluffs.media;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

interface MediaTokenIssuer {
    String issue(String roomName, String identity, String displayName, Duration ttl);
}

@Component
final class LiveKitMediaTokenIssuer implements MediaTokenIssuer {
    private static final byte[] HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
            .getBytes(StandardCharsets.UTF_8);
    private final MediaProperties properties;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    LiveKitMediaTokenIssuer(MediaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String issue(String roomName, String identity, String displayName, Duration ttl) {
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("roomJoin", true);
        video.put("room", roomName);
        video.put("canSubscribe", true);
        video.put("canPublish", true);
        video.put("canPublishData", false);
        video.put("canPublishSources", List.of("camera", "microphone"));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.apiKey());
        claims.put("exp", clock.instant().plus(ttl).getEpochSecond());
        claims.put("sub", identity);
        claims.put("name", displayName);
        claims.put("video", video);
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String unsigned = encoder.encodeToString(HEADER) + '.'
                    + encoder.encodeToString(json.writeValueAsBytes(claims));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return unsigned + '.' + encoder.encodeToString(
                    mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException | JacksonException exception) {
            throw new IllegalStateException("A LiveKit token could not be generated", exception);
        }
    }
}
