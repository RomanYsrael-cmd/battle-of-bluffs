package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.BattleOutcome;
import com.romanysrael.battleofbluffs.game.domain.PlayerSide;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.TerminalResult;
import java.util.List;
import java.util.UUID;

public record PublicMatchEvent(long sequence, Type type, PlayerSide actor, Position source,
                               Position destination, UUID attackerId, UUID defenderId,
                               BattleOutcome battleOutcome, List<UUID> removedPieceIds,
                               TerminalResult terminalResult) {
    public PublicMatchEvent { removedPieceIds = removedPieceIds == null ? List.of() : List.copyOf(removedPieceIds); }
    public enum Type { MATCH_CREATED, PLAYER_JOINED, PLAYER_LEFT, ROOM_CANCELLED,
        FORMATION_LOCKED, MATCH_STARTED, MOVE_APPLIED, BATTLE_RESOLVED,
        FLAG_CHALLENGE_STARTED, MATCH_ENDED, PLAYER_RESIGNED }
}
