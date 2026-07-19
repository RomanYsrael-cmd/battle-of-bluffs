package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PendingFlagChallenge(
        UUID advancedFlagId,
        Position flagPosition,
        PlayerSide respondingPlayer,
        Set<UUID> eligibleChallengerIds
) {
    public PendingFlagChallenge {
        Objects.requireNonNull(advancedFlagId, "advancedFlagId");
        Objects.requireNonNull(flagPosition, "flagPosition");
        Objects.requireNonNull(respondingPlayer, "respondingPlayer");
        eligibleChallengerIds = Set.copyOf(Objects.requireNonNull(eligibleChallengerIds, "eligibleChallengerIds"));
        if (eligibleChallengerIds.isEmpty()) {
            throw new IllegalArgumentException("A pending challenge requires at least one eligible challenger");
        }
    }
}
