package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;

public final class MoveValidator {

    public MoveValidationResult validate(MatchState state, Move move) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(move, "move");

        if (state.isTerminal()) {
            return rejected(MoveRejectionReason.MATCH_TERMINAL);
        }
        if (state.currentPlayer().orElse(null) != move.actingPlayer()) {
            return rejected(MoveRejectionReason.NOT_PLAYERS_TURN);
        }
        Piece piece = state.board().pieceAt(move.source()).orElse(null);
        if (piece == null) {
            return rejected(MoveRejectionReason.EMPTY_SOURCE);
        }
        if (piece.owner() != move.actingPlayer()) {
            return rejected(MoveRejectionReason.OPPONENT_PIECE);
        }
        if (move.source().equals(move.destination())) {
            return rejected(MoveRejectionReason.SAME_SOURCE_AND_DESTINATION);
        }
        if (!move.source().isOrthogonallyAdjacentTo(move.destination())) {
            return rejected(MoveRejectionReason.NOT_ORTHOGONALLY_ADJACENT);
        }
        Piece destinationPiece = state.board().pieceAt(move.destination()).orElse(null);
        if (destinationPiece != null && destinationPiece.owner() == move.actingPlayer()) {
            return rejected(MoveRejectionReason.ALLIED_DESTINATION);
        }
        if (state.pendingFlagChallenge().isPresent()) {
            PendingFlagChallenge challenge = state.pendingFlagChallenge().orElseThrow();
            if (!challenge.eligibleChallengerIds().contains(piece.id())
                    || !move.destination().equals(challenge.flagPosition())) {
                return rejected(MoveRejectionReason.FLAG_CHALLENGE_REQUIRED);
            }
        }
        return MoveValidationResult.accepted();
    }

    private MoveValidationResult rejected(MoveRejectionReason reason) {
        return MoveValidationResult.rejected(reason);
    }
}
