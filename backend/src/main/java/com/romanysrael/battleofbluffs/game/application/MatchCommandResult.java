package com.romanysrael.battleofbluffs.game.application;

import java.util.UUID;

public record MatchCommandResult(UUID commandId, long version, PlayerMatchView view) { }
