package com.romanysrael.battleofbluffs.matchmaking;

public final class MatchmakingException extends RuntimeException {
    private final String code;

    public MatchmakingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
