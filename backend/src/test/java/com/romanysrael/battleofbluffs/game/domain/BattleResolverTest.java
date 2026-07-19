package com.romanysrael.battleofbluffs.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class BattleResolverTest {
    private final BattleResolver resolver = new BattleResolver();

    @ParameterizedTest(name = "{0} attacks {1}")
    @MethodSource("allOrderedRankPairs")
    void resolvesEveryOrderedRankPair(Rank attacker, Rank defender) {
        BattleResult result = resolver.resolve(attacker, defender);

        assertEquals(expectedOutcome(attacker, defender), result.outcome());
        assertEquals(attacker == Rank.FLAG || defender == Rank.FLAG, result.flagCaptured());
        assertEquals(result.flagCaptured(), result.immediateTerminalOutcome());
        assertEquals(result.flagCaptured() ? TerminalReason.FLAG_CAPTURE : null, result.terminalReason());
    }

    @ParameterizedTest
    @EnumSource(value = Rank.class, names = "FLAG", mode = EnumSource.Mode.EXCLUDE)
    void equalNonFlagRanksSplit(Rank rank) {
        assertTrue(resolver.resolve(rank, rank).bothRemoved());
    }

    @Test
    void attackingFlagDefeatsDefendingFlagWithoutSplit() {
        BattleResult result = resolver.resolve(Rank.FLAG, Rank.FLAG);
        assertTrue(result.attackerSurvives());
        assertFalse(result.bothRemoved());
        assertTrue(result.flagCaptured());
    }

    private static Stream<Arguments> allOrderedRankPairs() {
        return Stream.of(Rank.values())
                .flatMap(attacker -> Stream.of(Rank.values())
                        .map(defender -> Arguments.of(attacker, defender)));
    }

    private BattleOutcome expectedOutcome(Rank attacker, Rank defender) {
        if (defender == Rank.FLAG) return BattleOutcome.ATTACKER_SURVIVES;
        if (attacker == Rank.FLAG) return BattleOutcome.DEFENDER_SURVIVES;
        if (attacker == defender) return BattleOutcome.BOTH_REMOVED;
        if (attacker == Rank.SPY) {
            return defender == Rank.PRIVATE ? BattleOutcome.DEFENDER_SURVIVES : BattleOutcome.ATTACKER_SURVIVES;
        }
        if (defender == Rank.SPY) {
            return attacker == Rank.PRIVATE ? BattleOutcome.ATTACKER_SURVIVES : BattleOutcome.DEFENDER_SURVIVES;
        }
        return attacker.ordinaryStrength() > defender.ordinaryStrength()
                ? BattleOutcome.ATTACKER_SURVIVES : BattleOutcome.DEFENDER_SURVIVES;
    }
}
