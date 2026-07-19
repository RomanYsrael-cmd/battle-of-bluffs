package com.romanysrael.battleofbluffs.game.domain;

public enum PlayerSide {
    PLAYER_ONE(0, 7, 1),
    PLAYER_TWO(7, 0, -1);

    private final int homeRow;
    private final int opponentBackRow;
    private final int forwardDelta;

    PlayerSide(int homeRow, int opponentBackRow, int forwardDelta) {
        this.homeRow = homeRow;
        this.opponentBackRow = opponentBackRow;
        this.forwardDelta = forwardDelta;
    }

    public int homeRow() {
        return homeRow;
    }

    public int opponentBackRow() {
        return opponentBackRow;
    }

    public int forwardDelta() {
        return forwardDelta;
    }

    public PlayerSide opponent() {
        return this == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
    }
}
