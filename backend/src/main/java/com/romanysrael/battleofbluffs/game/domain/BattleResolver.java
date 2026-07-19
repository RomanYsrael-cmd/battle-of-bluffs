package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;

public final class BattleResolver {

    public BattleResult resolve(Piece attacker, Piece defender) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        if (attacker.owner() == defender.owner()) {
            throw new IllegalArgumentException("A battle requires opposing owners");
        }
        return resolve(attacker.rank(), defender.rank());
    }

    public BattleResult resolve(Rank attacker, Rank defender) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");

        if (defender == Rank.FLAG) {
            return terminal(BattleOutcome.ATTACKER_SURVIVES);
        }
        if (attacker == Rank.FLAG) {
            return terminal(BattleOutcome.DEFENDER_SURVIVES);
        }
        if (attacker == defender) {
            return ordinary(BattleOutcome.BOTH_REMOVED);
        }
        if (attacker == Rank.SPY) {
            return ordinary(defender == Rank.PRIVATE
                    ? BattleOutcome.DEFENDER_SURVIVES : BattleOutcome.ATTACKER_SURVIVES);
        }
        if (defender == Rank.SPY) {
            return ordinary(attacker == Rank.PRIVATE
                    ? BattleOutcome.ATTACKER_SURVIVES : BattleOutcome.DEFENDER_SURVIVES);
        }
        return ordinary(attacker.ordinaryStrength() > defender.ordinaryStrength()
                ? BattleOutcome.ATTACKER_SURVIVES : BattleOutcome.DEFENDER_SURVIVES);
    }

    private BattleResult ordinary(BattleOutcome outcome) {
        return new BattleResult(outcome, false, null);
    }

    private BattleResult terminal(BattleOutcome outcome) {
        return new BattleResult(outcome, true, TerminalReason.FLAG_CAPTURE);
    }
}
