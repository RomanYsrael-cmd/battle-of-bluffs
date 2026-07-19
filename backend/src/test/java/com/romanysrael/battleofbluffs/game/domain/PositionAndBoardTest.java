package com.romanysrael.battleofbluffs.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionAndBoardTest {

    @Test
    void canonicalCoordinatesValidateAllBoundaries() {
        new Position(0, 0);
        new Position(7, 8);
        assertThrows(IllegalArgumentException.class, () -> new Position(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Position(8, 0));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 9));
    }

    @Test
    void adjacencyAndManhattanDistanceUseCanonicalCoordinates() {
        Position origin = new Position(3, 4);
        assertTrue(origin.isOrthogonallyAdjacentTo(new Position(3, 5)));
        assertEquals(2, origin.manhattanDistanceTo(new Position(4, 5)));
    }

    @Test
    void boardRejectsTwoLivePiecesOnOneCell() {
        Position position = new Position(1, 1);
        assertThrows(IllegalArgumentException.class, () -> new Board(List.of(
                piece(PlayerSide.PLAYER_ONE, Rank.FLAG, position),
                piece(PlayerSide.PLAYER_TWO, Rank.SPY, position))));
    }

    @Test
    void boardRejectsDuplicatePieceIdentifier() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Board(List.of(
                Piece.at(id, PlayerSide.PLAYER_ONE, Rank.FLAG, new Position(0, 0)),
                Piece.at(id, PlayerSide.PLAYER_ONE, Rank.FLAG, new Position(0, 1)))));
    }

    @Test
    void boardDoesNotExposeMutableCollections() {
        Board board = new Board(List.of(piece(PlayerSide.PLAYER_ONE, Rank.FLAG, new Position(0, 0))));
        assertThrows(UnsupportedOperationException.class, () -> board.pieces().clear());
    }

    @Test
    void replacementPreservesOwnerAndRankAndOccupancy() {
        Piece flag = piece(PlayerSide.PLAYER_ONE, Rank.FLAG, new Position(0, 0));
        Board moved = new Board(List.of(flag)).replace(flag.moveTo(new Position(1, 0)));
        assertTrue(moved.pieceAt(new Position(0, 0)).isEmpty());
        assertEquals(flag.id(), moved.pieceAt(new Position(1, 0)).orElseThrow().id());
    }

    private Piece piece(PlayerSide owner, Rank rank, Position position) {
        return Piece.at(UUID.randomUUID(), owner, rank, position);
    }
}
