package com.romanysrael.battleofbluffs.game.domain;

public enum Rank {
    FLAG(1),
    PRIVATE(2),
    SERGEANT(3),
    SECOND_LIEUTENANT(4),
    FIRST_LIEUTENANT(5),
    CAPTAIN(6),
    MAJOR(7),
    LIEUTENANT_COLONEL(8),
    COLONEL(9),
    ONE_STAR_GENERAL(10),
    TWO_STAR_GENERAL(11),
    THREE_STAR_GENERAL(12),
    FOUR_STAR_GENERAL(13),
    FIVE_STAR_GENERAL(14),
    SPY(null);

    private final Integer ordinaryStrength;

    Rank(Integer ordinaryStrength) {
        this.ordinaryStrength = ordinaryStrength;
    }

    public int ordinaryStrength() {
        if (ordinaryStrength == null) {
            throw new IllegalStateException("Spy has contextual battle strength");
        }
        return ordinaryStrength;
    }

    public boolean isOfficerOrGeneral() {
        return this != FLAG && this != PRIVATE && this != SPY;
    }
}
