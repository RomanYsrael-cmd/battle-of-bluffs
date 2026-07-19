package com.romanysrael.battleofbluffs.game.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class VictoryEvaluator {
    public static final int ACCEPTED_MOVE_LIMIT = 300;

    public VictoryEvaluation afterBattle(PlayerSide attacker, BattleResult battleResult) {
        if (battleResult.flagCaptured()) {
            PlayerSide winner = battleResult.attackerSurvives() ? attacker : attacker.opponent();
            return VictoryEvaluation.terminal(TerminalResult.win(winner, TerminalReason.FLAG_CAPTURE));
        }
        return VictoryEvaluation.ongoing();
    }

    public VictoryEvaluation afterMove(Board board, Piece movedPiece, int acceptedMoveCount) {
        return afterMove(board, movedPiece, acceptedMoveCount, 1);
    }

    public VictoryEvaluation afterMove(Board board, Piece movedPiece, int acceptedMoveCount,
                                       int authoritativePositionOccurrences) {
        if (movedPiece.rank() == Rank.FLAG
                && movedPiece.position().orElseThrow().row() == movedPiece.owner().opponentBackRow()) {
            Set<UUID> challengers = adjacentEnemyIds(board, movedPiece);
            if (challengers.isEmpty()) {
                return VictoryEvaluation.terminal(
                        TerminalResult.win(movedPiece.owner(), TerminalReason.FLAG_BACK_ROW));
            }
            return VictoryEvaluation.challenge(new PendingFlagChallenge(
                    movedPiece.id(), movedPiece.position().orElseThrow(), movedPiece.owner().opponent(), challengers));
        }
        if (acceptedMoveCount >= ACCEPTED_MOVE_LIMIT) {
            return VictoryEvaluation.terminal(TerminalResult.draw(TerminalReason.MOVE_LIMIT));
        }
        if (authoritativePositionOccurrences >= 3) {
            return VictoryEvaluation.terminal(TerminalResult.draw(TerminalReason.THREEFOLD_REPETITION));
        }
        return VictoryEvaluation.ongoing();
    }

    public VictoryEvaluation atTurnStart(MatchState state, MoveValidator validator) {
        PlayerSide player = state.currentPlayer().orElseThrow();
        boolean hasLegalMove = state.board().pieces().stream()
                .filter(Piece::isAlive)
                .filter(piece -> piece.owner() == player)
                .flatMap(piece -> neighboringPositions(piece.position().orElseThrow()).stream()
                        .map(destination -> new Move(player, piece.position().orElseThrow(), destination)))
                .anyMatch(move -> validator.validate(state, move).valid());
        return hasLegalMove ? VictoryEvaluation.ongoing()
                : VictoryEvaluation.terminal(TerminalResult.win(player.opponent(), TerminalReason.IMMOBILIZATION));
    }

    private Set<UUID> adjacentEnemyIds(Board board, Piece flag) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Position position : neighboringPositions(flag.position().orElseThrow())) {
            board.pieceAt(position)
                    .filter(piece -> piece.owner() != flag.owner())
                    .ifPresent(piece -> ids.add(piece.id()));
        }
        return ids;
    }

    private Set<Position> neighboringPositions(Position center) {
        Set<Position> positions = new LinkedHashSet<>();
        int[][] deltas = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] delta : deltas) {
            int row = center.row() + delta[0];
            int column = center.column() + delta[1];
            if (row >= 0 && row < Position.ROW_COUNT && column >= 0 && column < Position.COLUMN_COUNT) {
                positions.add(new Position(row, column));
            }
        }
        return positions;
    }
}
