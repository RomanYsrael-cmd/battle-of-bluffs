package com.romanysrael.battleofbluffs.game.application;

import com.romanysrael.battleofbluffs.game.domain.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlayerMatchView(UUID matchId, String roomCode, long version, MatchPhase phase,
                              String requestingPlayerId, PlayerSide requestingSide,
                              boolean playerOneOccupied, boolean playerTwoOccupied,
                              boolean playerOneLocked, boolean playerTwoLocked,
                              PlayerSide currentPlayer, List<OwnPieceView> ownPieces,
                              List<OpponentPieceView> opponentPieces, List<EventView> events,
                              PendingChallengeView pendingFlagChallenge, TerminalResult terminalResult,
                              List<RevealedPieceView> postMatchPieces) {
    public record OwnPieceView(UUID id, Rank rank, Position position, boolean alive) { }
    public record OpponentPieceView(UUID id, Position position) { }
    public record RevealedPieceView(UUID id, PlayerSide owner, Rank rank, Position position, boolean alive) { }
    public record PendingChallengeView(UUID advancedFlagId, Position flagPosition,
                                       PlayerSide respondingPlayer, Set<UUID> eligibleOwnChallengerIds) { }
    public record EventView(long sequence, PublicMatchEvent.Type type, PlayerSide actor,
                            Position source, Position destination, List<UUID> removedPieceIds,
                            String ownBattleOutcome, TerminalResult terminalResult) { }
}
