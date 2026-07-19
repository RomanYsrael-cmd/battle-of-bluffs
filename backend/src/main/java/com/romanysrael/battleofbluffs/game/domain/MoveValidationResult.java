package com.romanysrael.battleofbluffs.game.domain;

import java.util.Optional;

public record MoveValidationResult(boolean valid, MoveRejectionReason rejectionReason) {
    public MoveValidationResult {
        if (valid == (rejectionReason != null)) {
            throw new IllegalArgumentException("Valid results have no rejection reason; invalid results require one");
        }
    }

    public static MoveValidationResult accepted() {
        return new MoveValidationResult(true, null);
    }

    public static MoveValidationResult rejected(MoveRejectionReason reason) {
        return new MoveValidationResult(false, reason);
    }

    public Optional<MoveRejectionReason> reason() {
        return Optional.ofNullable(rejectionReason);
    }
}
