package com.romanysrael.battleofbluffs.media;

public final class MediaException extends RuntimeException {
    private final String code;

    public MediaException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
