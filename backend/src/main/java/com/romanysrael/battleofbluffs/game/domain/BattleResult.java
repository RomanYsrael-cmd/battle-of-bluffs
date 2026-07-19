package com.romanysrael.battleofbluffs.game.domain;

import java.util.Optional;

public record BattleResult(BattleOutcome outcome, boolean flagCaptured, TerminalReason terminalReason) {
    public BattleResult {
        if (flagCaptured != (terminalReason == TerminalReason.FLAG_CAPTURE)) {
            throw new IllegalArgumentException("Flag capture and terminal reason must agree");
        }
    }

    public boolean attackerSurvives() { return outcome == BattleOutcome.ATTACKER_SURVIVES; }
    public boolean defenderSurvives() { return outcome == BattleOutcome.DEFENDER_SURVIVES; }
    public boolean bothRemoved() { return outcome == BattleOutcome.BOTH_REMOVED; }
    public boolean immediateTerminalOutcome() { return terminalReason != null; }
    public Optional<TerminalReason> terminalReasonOptional() { return Optional.ofNullable(terminalReason); }
}
