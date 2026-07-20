package com.romanysrael.battleofbluffs.media;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record MediaProperties(
        boolean enabled,
        String url,
        String apiKey,
        String apiSecret,
        Duration tokenTtl,
        Duration postMatchWindow) {

    public MediaProperties(
            @Value("${app.media.enabled:false}") boolean enabled,
            @Value("${app.media.url:}") String url,
            @Value("${app.media.api-key:}") String apiKey,
            @Value("${app.media.api-secret:}") String apiSecret,
            @Value("${app.media.token-ttl:5m}") Duration tokenTtl,
            @Value("${app.media.post-match-window:10m}") Duration postMatchWindow) {
        this.enabled = enabled;
        this.url = url == null ? "" : url.strip();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.apiSecret = apiSecret == null ? "" : apiSecret.strip();
        this.tokenTtl = tokenTtl;
        this.postMatchWindow = postMatchWindow;
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()
                || tokenTtl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("LiveKit token TTL must be positive and no longer than five minutes");
        }
        if (postMatchWindow == null || postMatchWindow.isZero() || postMatchWindow.isNegative()) {
            throw new IllegalArgumentException("Media post-match window must be positive");
        }
    }

    @Override
    public String toString() {
        return "MediaProperties[enabled=" + enabled + ", url=" + url
                + ", apiKey=REDACTED, apiSecret=REDACTED, tokenTtl=" + tokenTtl
                + ", postMatchWindow=" + postMatchWindow + ']';
    }
}
