package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;
import java.util.Optional;

public final class MatchState {
    private final Board board;
    private final PlayerSide currentPlayer;
    private final MatchPhase phase;
    private final TerminalResult terminalResult;
    private final PendingFlagChallenge pendingFlagChallenge;
    private final int acceptedMoveCount;
    private final int currentPositionOccurrences;

    private MatchState(Board board, PlayerSide currentPlayer, MatchPhase phase,
                       TerminalResult terminalResult, PendingFlagChallenge pendingFlagChallenge,
                       int acceptedMoveCount, int currentPositionOccurrences) {
        this.board = Objects.requireNonNull(board, "board");
        this.currentPlayer = currentPlayer;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.terminalResult = terminalResult;
        this.pendingFlagChallenge = pendingFlagChallenge;
        if (acceptedMoveCount < 0) {
            throw new IllegalArgumentException("Accepted move count cannot be negative");
        }
        this.acceptedMoveCount = acceptedMoveCount;
        if (currentPositionOccurrences < 1) {
            throw new IllegalArgumentException("The current position must have occurred at least once");
        }
        this.currentPositionOccurrences = currentPositionOccurrences;
        if (phase == MatchPhase.ACTIVE && currentPlayer == null) {
            throw new IllegalArgumentException("An active match requires a current player");
        }
        if ((phase == MatchPhase.TERMINAL) != (terminalResult != null)) {
            throw new IllegalArgumentException("Terminal phase and result must be present together");
        }
        if (pendingFlagChallenge != null && (phase != MatchPhase.ACTIVE
                || pendingFlagChallenge.respondingPlayer() != currentPlayer)) {
            throw new IllegalArgumentException("Pending challenge must belong to the active player");
        }
    }

    public static MatchState active(Board board, PlayerSide currentPlayer) {
        return new MatchState(board, currentPlayer, MatchPhase.ACTIVE, null, null, 0, 1);
    }

    public static MatchState active(Board board, PlayerSide currentPlayer, int acceptedMoveCount,
                                    PendingFlagChallenge challenge) {
        return active(board, currentPlayer, acceptedMoveCount, challenge, 1);
    }

    public static MatchState active(Board board, PlayerSide currentPlayer, int acceptedMoveCount,
                                    PendingFlagChallenge challenge, int currentPositionOccurrences) {
        return new MatchState(board, currentPlayer, MatchPhase.ACTIVE, null, challenge,
                acceptedMoveCount, currentPositionOccurrences);
    }

    public static MatchState terminal(Board board, TerminalResult result, int acceptedMoveCount) {
        return new MatchState(board, null, MatchPhase.TERMINAL, result, null, acceptedMoveCount, 1);
    }

    public Board board() { return board; }
    public Optional<PlayerSide> currentPlayer() { return Optional.ofNullable(currentPlayer); }
    public MatchPhase phase() { return phase; }
    public boolean isTerminal() { return phase == MatchPhase.TERMINAL; }
    public Optional<TerminalResult> terminalResult() { return Optional.ofNullable(terminalResult); }
    public Optional<PendingFlagChallenge> pendingFlagChallenge() { return Optional.ofNullable(pendingFlagChallenge); }
    public int acceptedMoveCount() { return acceptedMoveCount; }
    public int currentPositionOccurrences() { return currentPositionOccurrences; }
}
