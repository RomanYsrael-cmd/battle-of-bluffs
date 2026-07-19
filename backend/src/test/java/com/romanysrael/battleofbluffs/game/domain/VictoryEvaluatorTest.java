package com.romanysrael.battleofbluffs.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VictoryEvaluatorTest {
    private final VictoryEvaluator evaluator = new VictoryEvaluator();

    @Test
    void flagCaptureAwardsWinnerBasedOnSurvivor() {
        VictoryEvaluation attackerWins = evaluator.afterBattle(PlayerSide.PLAYER_ONE,
                new BattleResolver().resolve(Rank.SPY, Rank.FLAG));
        assertWinner(attackerWins, PlayerSide.PLAYER_ONE, TerminalReason.FLAG_CAPTURE);

        VictoryEvaluation defenderWins = evaluator.afterBattle(PlayerSide.PLAYER_ONE,
                new BattleResolver().resolve(Rank.FLAG, Rank.PRIVATE));
        assertWinner(defenderWins, PlayerSide.PLAYER_TWO, TerminalReason.FLAG_CAPTURE);
    }

    @Test
    void unchallengedFlagOnBackRowWinsImmediately() {
        Piece flag = piece(PlayerSide.PLAYER_ONE, Rank.FLAG, 7, 4);
        VictoryEvaluation result = evaluator.afterMove(new Board(List.of(flag)), flag, 20);
        assertWinner(result, PlayerSide.PLAYER_ONE, TerminalReason.FLAG_BACK_ROW);
    }

    @Test
    void adjacentEnemyCreatesPendingFlagChallenge() {
        Piece flag = piece(PlayerSide.PLAYER_ONE, Rank.FLAG, 7, 4);
        Piece enemy = piece(PlayerSide.PLAYER_TWO, Rank.PRIVATE, 6, 4);
        VictoryEvaluation result = evaluator.afterMove(new Board(List.of(flag, enemy)), flag, 20);
        assertEquals(java.util.Set.of(enemy.id()), result.challenge().orElseThrow().eligibleChallengerIds());
    }

    @Test
    void threeHundredthAcceptedMoveDraws() {
        Piece piece = piece(PlayerSide.PLAYER_ONE, Rank.PRIVATE, 4, 4);
        VictoryEvaluation result = evaluator.afterMove(new Board(List.of(piece)), piece, 300);
        assertEquals(TerminalReason.MOVE_LIMIT, result.result().orElseThrow().reason());
        assertTrue(result.result().orElseThrow().winningSide().isEmpty());
    }

    @Test
    void thirdAuthoritativePositionOccurrenceDraws() {
        Piece piece = piece(PlayerSide.PLAYER_ONE, Rank.PRIVATE, 4, 4);
        VictoryEvaluation result = evaluator.afterMove(new Board(List.of(piece)), piece, 40, 3);
        assertEquals(TerminalReason.THREEFOLD_REPETITION, result.result().orElseThrow().reason());
    }

    @Test
    void playerWithNoLegalMoveLosesByImmobilization() {
        MatchState state = MatchState.active(Board.empty(), PlayerSide.PLAYER_ONE);
        assertWinner(evaluator.atTurnStart(state, new MoveValidator()), PlayerSide.PLAYER_TWO,
                TerminalReason.IMMOBILIZATION);
    }

    private void assertWinner(VictoryEvaluation evaluation, PlayerSide winner, TerminalReason reason) {
        TerminalResult result = evaluation.result().orElseThrow();
        assertEquals(winner, result.winningSide().orElseThrow());
        assertEquals(reason, result.reason());
    }

    private Piece piece(PlayerSide owner, Rank rank, int row, int column) {
        return Piece.at(UUID.randomUUID(), owner, rank, new Position(row, column));
    }
}
