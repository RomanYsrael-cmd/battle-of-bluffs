package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import java.util.List;
import java.util.UUID;

public final class Commands {
    private Commands() { }

    public record CreateMatchCommand(String playerId) { }
    public record JoinMatchCommand(UUID commandId, String roomCode, String playerId, long expectedVersion) { }
    public record FormationPiece(UUID pieceId, Rank rank, Position position) { }
    public record SubmitFormationCommand(UUID commandId, UUID matchId, String playerId,
                                         long expectedVersion, List<FormationPiece> pieces) { }
    public record LockFormationCommand(UUID commandId, UUID matchId, String playerId, long expectedVersion) { }
    public record MakeMoveCommand(UUID commandId, UUID matchId, String playerId, long expectedVersion,
                                  Position source, Position destination) { }
    public record ResignCommand(UUID commandId, UUID matchId, String playerId, long expectedVersion) { }
}
