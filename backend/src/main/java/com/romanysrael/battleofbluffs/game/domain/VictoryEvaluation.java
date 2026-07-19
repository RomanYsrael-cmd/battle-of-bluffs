package com.romanysrael.battleofbluffs.game.domain;

import java.util.Optional;

public record VictoryEvaluation(TerminalResult terminalResult, PendingFlagChallenge pendingChallenge) {
    public VictoryEvaluation {
        if (terminalResult != null && pendingChallenge != null) {
            throw new IllegalArgumentException("An evaluation cannot be terminal and pending");
        }
    }

    public static VictoryEvaluation ongoing() { return new VictoryEvaluation(null, null); }
    public static VictoryEvaluation terminal(TerminalResult result) { return new VictoryEvaluation(result, null); }
    public static VictoryEvaluation challenge(PendingFlagChallenge challenge) { return new VictoryEvaluation(null, challenge); }
    public Optional<TerminalResult> result() { return Optional.ofNullable(terminalResult); }
    public Optional<PendingFlagChallenge> challenge() { return Optional.ofNullable(pendingChallenge); }
}
