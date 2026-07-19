package com.romanysrael.battleofbluffs.game.application;

public final class MatchApplicationException extends RuntimeException {
    private final MatchErrorCode code;

    public MatchApplicationException(MatchErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public MatchErrorCode code() { return code; }
}
