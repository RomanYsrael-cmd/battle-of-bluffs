package com.romanysrael.battleofbluffs.game.domain;

public enum MoveRejectionReason {
    MATCH_TERMINAL,
    NOT_PLAYERS_TURN,
    EMPTY_SOURCE,
    OPPONENT_PIECE,
    SAME_SOURCE_AND_DESTINATION,
    NOT_ORTHOGONALLY_ADJACENT,
    ALLIED_DESTINATION,
    FLAG_CHALLENGE_REQUIRED
}
