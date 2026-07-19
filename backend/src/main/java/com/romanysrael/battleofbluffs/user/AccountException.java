package com.romanysrael.battleofbluffs.user;

public final class AccountException extends RuntimeException {
    private final String code;

    public AccountException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
