package com.romanysrael.battleofbluffs.game.web;

import static com.romanysrael.battleofbluffs.game.application.Commands.*;

import com.romanysrael.battleofbluffs.game.application.*;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Development-only API. playerId is an insecure temporary credential, not authentication. */
@RestController
@RequestMapping("/api/dev/matches")
public final class DevelopmentMatchController {
    private final MatchApplicationService service;

    public DevelopmentMatchController(MatchApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DevCommandResponse create(@Valid @RequestBody(required=false) CreateRequest request) {
        MatchCommandResult result = service.createMatch(
                new CreateMatchCommand(request == null ? null : request.playerId()));
        return response(result);
    }

    @PostMapping("/{matchId}/join")
    public DevCommandResponse join(
            @PathVariable UUID matchId, @Valid @RequestBody JoinRequest request) {
        MatchCommandResult result = service.joinMatch(matchId, new JoinMatchCommand(
                request.commandId(), request.roomCode(), request.playerId(),
                request.expectedVersion()));
        return response(result);
    }

    @PutMapping("/{matchId}/formation")
    public DevCommandResponse formation(
            @PathVariable UUID matchId, @Valid @RequestBody FormationRequest request) {
        List<FormationPiece> pieces = request.pieces().stream()
                .map(piece -> new FormationPiece(
                        piece.pieceId(), piece.rank(),
                        new com.romanysrael.battleofbluffs.game.domain.Position(
                                piece.row(), piece.column())))
                .toList();
        return response(service.submitFormation(new SubmitFormationCommand(
                request.commandId(), matchId, request.playerId(),
                request.expectedVersion(), pieces)));
    }

    @PostMapping("/{matchId}/lock")
    public DevCommandResponse lock(
            @PathVariable UUID matchId, @Valid @RequestBody CommandRequest request) {
        return response(service.lockFormation(new LockFormationCommand(
                request.commandId(), matchId, request.playerId(), request.expectedVersion())));
    }

    @PostMapping("/{matchId}/moves")
    public DevCommandResponse move(
            @PathVariable UUID matchId, @Valid @RequestBody MoveRequest request) {
        return response(service.makeMove(new MakeMoveCommand(
                request.commandId(), matchId, request.playerId(), request.expectedVersion(),
                new com.romanysrael.battleofbluffs.game.domain.Position(
                        request.source().row(), request.source().column()),
                new com.romanysrael.battleofbluffs.game.domain.Position(
                        request.destination().row(), request.destination().column()))));
    }

    @PostMapping("/{matchId}/resign")
    public DevCommandResponse resign(
            @PathVariable UUID matchId, @Valid @RequestBody CommandRequest request) {
        return response(service.resign(new ResignCommand(
                request.commandId(), matchId, request.playerId(), request.expectedVersion())));
    }

    @GetMapping("/{matchId}")
    public PlayerMatchView view(
            @PathVariable UUID matchId, @RequestParam @NotBlank String playerId) {
        return service.getView(matchId, playerId);
    }

    private DevCommandResponse response(MatchCommandResult result) {
        return new DevCommandResponse(
                result.commandId(), result.version(), result.view().requestingPlayerId(),
                result.view().matchId(), result.view().roomCode(), result.view());
    }

    public record CreateRequest(String playerId) { }

    public record JoinRequest(
            @NotNull UUID commandId, @NotBlank String roomCode, String playerId,
            @Positive long expectedVersion) { }

    public record CommandRequest(
            @NotNull UUID commandId, @NotBlank String playerId,
            @Positive long expectedVersion) { }

    public record Cell(
            @Min(0) @Max(7) int row, @Min(0) @Max(8) int column) { }

    public record FormationItem(
            @NotNull UUID pieceId, @NotNull Rank rank,
            @Min(0) @Max(7) int row, @Min(0) @Max(8) int column) { }

    public record FormationRequest(
            @NotNull UUID commandId, @NotBlank String playerId,
            @Positive long expectedVersion,
            @NotNull @Size(min = 21, max = 21) List<@Valid FormationItem> pieces) { }

    public record MoveRequest(
            @NotNull UUID commandId, @NotBlank String playerId,
            @Positive long expectedVersion, @NotNull @Valid Cell source,
            @NotNull @Valid Cell destination) { }

    public record DevCommandResponse(
            UUID commandId, long version, String playerId, UUID matchId,
            String roomCode, PlayerMatchView view) { }
}
