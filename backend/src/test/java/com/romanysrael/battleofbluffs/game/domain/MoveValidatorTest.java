package com.romanysrael.battleofbluffs.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MoveValidatorTest {
    private final MoveValidator validator = new MoveValidator();

    @Test
    void acceptsLegalOrthogonalMovementForOrdinaryPieceAndFlag() {
        assertTrue(validate(piece(PlayerSide.PLAYER_ONE, Rank.CAPTAIN, 2, 2), 2, 2, 3, 2).valid());
        assertTrue(validate(piece(PlayerSide.PLAYER_ONE, Rank.FLAG, 2, 2), 2, 2, 2, 3).valid());
    }

    @Test
    void rejectsDiagonalMovement() {
        assertReason(validate(piece(PlayerSide.PLAYER_ONE, Rank.SPY, 2, 2), 2, 2, 3, 3),
                MoveRejectionReason.NOT_ORTHOGONALLY_ADJACENT);
    }

    @Test
    void rejectsMovementBeyondOneSquare() {
        assertReason(validate(piece(PlayerSide.PLAYER_ONE, Rank.SPY, 2, 2), 2, 2, 4, 2),
                MoveRejectionReason.NOT_ORTHOGONALLY_ADJACENT);
    }

    @Test
    void rejectsMovementOntoAlliedPiece() {
        Piece mover = piece(PlayerSide.PLAYER_ONE, Rank.SPY, 2, 2);
        Piece ally = piece(PlayerSide.PLAYER_ONE, Rank.PRIVATE, 3, 2);
        MatchState state = MatchState.active(new Board(List.of(mover, ally)), PlayerSide.PLAYER_ONE);
        assertReason(validator.validate(state, move(2, 2, 3, 2)), MoveRejectionReason.ALLIED_DESTINATION);
    }

    @Test
    void acceptsAttackAgainstAdjacentEnemy() {
        Piece mover = piece(PlayerSide.PLAYER_ONE, Rank.SPY, 2, 2);
        Piece enemy = piece(PlayerSide.PLAYER_TWO, Rank.PRIVATE, 3, 2);
        MatchState state = MatchState.active(new Board(List.of(mover, enemy)), PlayerSide.PLAYER_ONE);
        assertTrue(validator.validate(state, move(2, 2, 3, 2)).valid());
    }

    @Test
    void rejectsMovingOpponentPiece() {
        Piece enemy = piece(PlayerSide.PLAYER_TWO, Rank.PRIVATE, 2, 2);
        assertReason(validate(enemy, 2, 2, 3, 2), MoveRejectionReason.OPPONENT_PIECE);
    }

    @Test
    void rejectsWrongTurnAndEmptySource() {
        Piece mover = piece(PlayerSide.PLAYER_ONE, Rank.SPY, 2, 2);
        MatchState wrongTurn = MatchState.active(new Board(List.of(mover)), PlayerSide.PLAYER_TWO);
        assertReason(validator.validate(wrongTurn, move(2, 2, 3, 2)), MoveRejectionReason.NOT_PLAYERS_TURN);
        MatchState empty = MatchState.active(Board.empty(), PlayerSide.PLAYER_ONE);
        assertReason(validator.validate(empty, move(2, 2, 3, 2)), MoveRejectionReason.EMPTY_SOURCE);
    }

    @Test
    void rejectsMoveAfterTerminalMatch() {
        Piece mover = piece(PlayerSide.PLAYER_ONE, Rank.FLAG, 2, 2);
        MatchState terminal = MatchState.terminal(new Board(List.of(mover)),
                TerminalResult.win(PlayerSide.PLAYER_ONE, TerminalReason.FLAG_CAPTURE), 1);
        assertReason(validator.validate(terminal, move(2, 2, 3, 2)), MoveRejectionReason.MATCH_TERMINAL);
    }

    @Test
    void pendingChallengeAcceptsOnlyEligibleCapture() {
        Piece advancedFlag = piece(PlayerSide.PLAYER_ONE, Rank.FLAG, 7, 4);
        Piece challenger = piece(PlayerSide.PLAYER_TWO, Rank.PRIVATE, 6, 4);
        PendingFlagChallenge pending = new PendingFlagChallenge(advancedFlag.id(), new Position(7, 4),
                PlayerSide.PLAYER_TWO, java.util.Set.of(challenger.id()));
        MatchState state = MatchState.active(new Board(List.of(advancedFlag, challenger)),
                PlayerSide.PLAYER_TWO, 10, pending);
        assertTrue(validator.validate(state,
                new Move(PlayerSide.PLAYER_TWO, new Position(6, 4), new Position(7, 4))).valid());
        assertReason(validator.validate(state,
                new Move(PlayerSide.PLAYER_TWO, new Position(6, 4), new Position(6, 5))),
                MoveRejectionReason.FLAG_CHALLENGE_REQUIRED);
    }

    private MoveValidationResult validate(Piece piece, int sourceRow, int sourceColumn, int row, int column) {
        return validator.validate(MatchState.active(new Board(List.of(piece)), PlayerSide.PLAYER_ONE),
                new Move(PlayerSide.PLAYER_ONE, new Position(sourceRow, sourceColumn), new Position(row, column)));
    }

    private Move move(int sourceRow, int sourceColumn, int row, int column) {
        return new Move(PlayerSide.PLAYER_ONE, new Position(sourceRow, sourceColumn), new Position(row, column));
    }

    private Piece piece(PlayerSide owner, Rank rank, int row, int column) {
        return Piece.at(UUID.randomUUID(), owner, rank, new Position(row, column));
    }

    private void assertReason(MoveValidationResult result, MoveRejectionReason expected) {
        assertEquals(expected, result.reason().orElseThrow());
    }
}
