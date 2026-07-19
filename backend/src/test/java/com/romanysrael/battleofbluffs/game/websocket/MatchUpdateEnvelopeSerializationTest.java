package com.romanysrael.battleofbluffs.game.websocket;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.application.MatchUpdatePublisher.UpdateType;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import com.romanysrael.battleofbluffs.game.application.TimerMode;
import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

class MatchUpdateEnvelopeSerializationTest {
    @Test
    void jacksonThreeConverterSerializesTheBrokerEnvelope() {
        UUID matchId = UUID.randomUUID();
        PlayerMatchView view = new PlayerMatchView(
                matchId,
                "ABC234",
                2,
                2,
                MatchPhase.FORMATION,
                MatchMode.CASUAL,
                TimerMode.CASUAL_UNTIMED,
                UUID.randomUUID().toString(),
                PlayerSide.PLAYER_ONE,
                true,
                true,
                false,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null);
        MatchUpdateEnvelope envelope = new MatchUpdateEnvelope(
                UpdateType.PLAYER_JOINED,
                matchId,
                2,
                2,
                Instant.parse("2026-07-19T10:15:30Z"),
                view);
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(
                JsonMapper.builder().findAndAddModules().build());

        Message<?> message = converter.toMessage(envelope, null);

        byte[] payload = assertInstanceOf(byte[].class, message.getPayload());
        String json = new String(payload, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"type\":\"PLAYER_JOINED\""));
        assertTrue(json.contains("\"serverTimestamp\":\"2026-07-19T10:15:30Z\""));
        assertTrue(json.contains("\"view\""));
        assertThat(view.toString()).doesNotContain("ownPieces", "opponentPieces", "rank");
    }
}
