package com.romanysrael.battleofbluffs.matchmaking;

public final class MatchmakingException extends RuntimeException {
    private final String code;
    private final Object context;

    public MatchmakingException(String code, String message) {
        this(code, message, null);
    }

    public MatchmakingException(String code, String message, Object context) {
        super(message);
        this.code = code;
        this.context = context;
    }

    public String code() {
        return code;
    }

    public Object context() {
        return context;
    }
}
