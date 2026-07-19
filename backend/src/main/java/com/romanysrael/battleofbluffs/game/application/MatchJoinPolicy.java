package com.romanysrael.battleofbluffs.game.application;

@FunctionalInterface
public interface MatchJoinPolicy {
    MatchJoinPolicy ALLOW_ALL = (existingPlayerId, joiningPlayerId) -> true;

    boolean mayJoin(String existingPlayerId, String joiningPlayerId);
}
