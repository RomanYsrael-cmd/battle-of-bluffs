package com.romanysrael.battleofbluffs.game.application;

import static com.romanysrael.battleofbluffs.game.application.Commands.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.romanysrael.battleofbluffs.game.domain.MatchPhase;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MatchSnapshotCodecTest {
    private final PlayerMatchViewMapper mapper = new PlayerMatchViewMapper();
    private final MatchSnapshotCodec codec = new MatchSnapshotCodec(JsonMapper.builder().build());

    @Test
    void restoresActiveAggregateAndAcceptedCommandReplay() {
        InMemoryMatchRepository originalRepository = new InMemoryMatchRepository();
        MatchApplicationService original = new MatchApplicationService(originalRepository, mapper);
        MatchCommandResult created = original.createMatch(new CreateMatchCommand(
                "alice", MatchMode.CASUAL, TimerMode.STANDARD_15_PLUS_5));
        UUID joinId = UUID.randomUUID();
        JoinMatchCommand join = new JoinMatchCommand(
                joinId, created.view().roomCode(), "bob", 1);
        original.joinMatch(join);
        UUID matchId = created.view().matchId();
        original.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), matchId, "alice", 2, formation(PlayerSide.PLAYER_ONE)));
        original.submitFormation(new SubmitFormationCommand(
                UUID.randomUUID(), matchId, "bob", 3, formation(PlayerSide.PLAYER_TWO)));
        original.lockFormation(new LockFormationCommand(UUID.randomUUID(), matchId, "alice", 4));
        original.lockFormation(new LockFormationCommand(UUID.randomUUID(), matchId, "bob", 5));

        String encoded = codec.encode(originalRepository.findById(matchId).orElseThrow());
        PrivateMatch restored = codec.decode(encoded);
        InMemoryMatchRepository restoredRepository = new InMemoryMatchRepository();
        restoredRepository.save(restored);
        MatchApplicationService restarted = new MatchApplicationService(restoredRepository, mapper);

        PlayerMatchView alice = restarted.getView(matchId, "alice");
        assertEquals(MatchPhase.ACTIVE, alice.phase());
        assertEquals(MatchMode.CASUAL, alice.mode());
        assertEquals(TimerMode.STANDARD_15_PLUS_5, alice.timerMode());
        assertEquals(6, alice.version());
        assertEquals(21, alice.ownPieces().size());
        assertEquals(21, alice.opponentPieces().size());
        assertFalse(alice.toString().contains("opponentPieces=[OpponentPieceView[id=FLAG"));

        MatchCommandResult replayedJoin = restarted.joinMatch(join);
        assertEquals(2, replayedJoin.version());
        assertEquals(joinId, replayedJoin.commandId());
    }

    @Test
    void olderSnapshotWithoutLiveSequenceFallsBackToAggregateVersion() {
        InMemoryMatchRepository repository = new InMemoryMatchRepository();
        MatchApplicationService service = new MatchApplicationService(repository, mapper);
        MatchCommandResult created = service.createMatch(new CreateMatchCommand("legacy-player"));
        String encoded = codec.encode(repository.findById(created.view().matchId()).orElseThrow());
        String legacyEncoded = encoded.replace("\"liveSequence\":1,", "");

        PrivateMatch restored = codec.decode(legacyEncoded);

        assertEquals(restored.version, restored.liveSequence);
    }

    private static List<FormationPiece> formation(PlayerSide side) {
        List<Rank> ranks = new ArrayList<>(List.of(
                Rank.FIVE_STAR_GENERAL, Rank.FOUR_STAR_GENERAL,
                Rank.THREE_STAR_GENERAL, Rank.TWO_STAR_GENERAL,
                Rank.ONE_STAR_GENERAL, Rank.COLONEL, Rank.LIEUTENANT_COLONEL,
                Rank.MAJOR, Rank.CAPTAIN, Rank.FIRST_LIEUTENANT,
                Rank.SECOND_LIEUTENANT, Rank.SERGEANT, Rank.SPY, Rank.SPY,
                Rank.FLAG));
        for (int index = 0; index < 6; index++) {
            ranks.add(Rank.PRIVATE);
        }
        int startingRow = side == PlayerSide.PLAYER_ONE ? 0 : 5;
        List<FormationPiece> pieces = new ArrayList<>();
        for (int index = 0; index < ranks.size(); index++) {
            pieces.add(new FormationPiece(
                    UUID.randomUUID(),
                    ranks.get(index),
                    new Position(startingRow + index / 9, index % 9)));
        }
        return pieces;
    }
}
