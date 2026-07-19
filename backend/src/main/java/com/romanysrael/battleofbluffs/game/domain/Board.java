package com.romanysrael.battleofbluffs.game.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Board {
    private final Map<UUID, Piece> piecesById;
    private final Map<Position, UUID> livePieceIdsByPosition;

    public Board(Collection<Piece> pieces) {
        Objects.requireNonNull(pieces, "pieces");
        Map<UUID, Piece> byId = new LinkedHashMap<>();
        Map<Position, UUID> byPosition = new LinkedHashMap<>();
        for (Piece piece : pieces) {
            Objects.requireNonNull(piece, "piece");
            if (byId.putIfAbsent(piece.id(), piece) != null) {
                throw new IllegalArgumentException("Duplicate piece identifier: " + piece.id());
            }
            piece.position().ifPresent(position -> {
                if (byPosition.putIfAbsent(position, piece.id()) != null) {
                    throw new IllegalArgumentException("Multiple live pieces occupy " + position);
                }
            });
        }
        piecesById = Collections.unmodifiableMap(byId);
        livePieceIdsByPosition = Collections.unmodifiableMap(byPosition);
    }

    public static Board empty() {
        return new Board(java.util.List.of());
    }

    public Optional<Piece> pieceAt(Position position) {
        Objects.requireNonNull(position, "position");
        return Optional.ofNullable(livePieceIdsByPosition.get(position)).map(piecesById::get);
    }

    public Optional<Piece> pieceById(UUID id) {
        return Optional.ofNullable(piecesById.get(Objects.requireNonNull(id, "id")));
    }

    public Collection<Piece> pieces() {
        return piecesById.values();
    }

    public Board replace(Piece... replacements) {
        Map<UUID, Piece> updated = new LinkedHashMap<>(piecesById);
        for (Piece replacement : replacements) {
            Objects.requireNonNull(replacement, "replacement");
            if (!updated.containsKey(replacement.id())) {
                throw new IllegalArgumentException("Cannot replace unknown piece: " + replacement.id());
            }
            Piece existing = updated.get(replacement.id());
            if (existing.owner() != replacement.owner() || existing.rank() != replacement.rank()) {
                throw new IllegalArgumentException("Piece owner and rank are immutable");
            }
            updated.put(replacement.id(), replacement);
        }
        return new Board(updated.values());
    }
}
