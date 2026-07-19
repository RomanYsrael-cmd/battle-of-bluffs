package com.romanysrael.battleofbluffs.game.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Piece {
    private final UUID id;
    private final PlayerSide owner;
    private final Rank rank;
    private final Position position;

    private Piece(UUID id, PlayerSide owner, Rank rank, Position position) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.rank = Objects.requireNonNull(rank, "rank");
        this.position = position;
    }

    public static Piece at(UUID id, PlayerSide owner, Rank rank, Position position) {
        return new Piece(id, owner, rank, Objects.requireNonNull(position, "position"));
    }

    public static Piece removed(UUID id, PlayerSide owner, Rank rank) {
        return new Piece(id, owner, rank, null);
    }

    public UUID id() {
        return id;
    }

    public PlayerSide owner() {
        return owner;
    }

    public Rank rank() {
        return rank;
    }

    public Optional<Position> position() {
        return Optional.ofNullable(position);
    }

    public boolean isAlive() {
        return position != null;
    }

    public Piece moveTo(Position destination) {
        if (!isAlive()) {
            throw new IllegalStateException("A removed piece cannot move");
        }
        return new Piece(id, owner, rank, Objects.requireNonNull(destination, "destination"));
    }

    public Piece remove() {
        return new Piece(id, owner, rank, null);
    }
}
