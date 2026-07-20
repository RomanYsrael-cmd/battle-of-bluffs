package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MediaMatchContextTest {
    @Test
    void mediaAuthorizationReadsMembershipWithoutMutatingAuthoritativeMatchState() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC);
        PlayerMatchViewMapper mapper = new PlayerMatchViewMapper();
        mapper.setClock(clock);
        MatchApplicationService matches = new MatchApplicationService(
                new InMemoryMatchRepository(), mapper);
        matches.setClock(clock);
        UUID first = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("20000000-0000-4000-8000-000000000002");
        MatchCommandResult created = matches.createMatch(new CreateMatchCommand(first.toString()));
        matches.joinMatch(new JoinMatchCommand(
                UUID.randomUUID(), created.view().roomCode(), second.toString(), created.version()));
        PlayerMatchView before = matches.getView(created.view().matchId(), first.toString());

        MatchApplicationService.MediaMatchContext context =
                matches.mediaContext(created.view().matchId(), first.toString());
        PlayerMatchView after = matches.getView(created.view().matchId(), first.toString());

        assertThat(context.matchId()).isEqualTo(created.view().matchId());
        assertThat(context.opponentId()).isEqualTo(second.toString());
        assertThat(context.participantCycleStartedAt()).isEqualTo(clock.instant());
        assertThat(context.terminal()).isFalse();
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after.liveSequence()).isEqualTo(before.liveSequence());
        assertThat(after.events()).isEqualTo(before.events());
        assertThat(after.presence()).isEqualTo(before.presence());
        assertThat(after.timer()).isEqualTo(before.timer());
        assertThat(after.terminalResult()).isNull();
        assertThatThrownBy(() -> matches.mediaContext(
                created.view().matchId(), UUID.randomUUID().toString()))
                .isInstanceOf(MatchApplicationException.class)
                .extracting(exception -> ((MatchApplicationException) exception).code())
                .isEqualTo(MatchErrorCode.PLAYER_NOT_IN_MATCH);
    }
}
