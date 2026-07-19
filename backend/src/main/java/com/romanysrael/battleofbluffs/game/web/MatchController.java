package com.romanysrael.battleofbluffs.game.web;

import static com.romanysrael.battleofbluffs.game.application.Commands.FormationPiece;
import static com.romanysrael.battleofbluffs.game.application.Commands.CancelMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.JoinMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.LockFormationCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.LeaveMatchCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.MakeMoveCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.ResignCommand;
import static com.romanysrael.battleofbluffs.game.application.Commands.SubmitFormationCommand;

import com.romanysrael.battleofbluffs.game.application.Commands.CreateMatchCommand;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService;
import com.romanysrael.battleofbluffs.game.application.MatchCommandResult;
import com.romanysrael.battleofbluffs.game.application.MatchLifecycleResult;
import com.romanysrael.battleofbluffs.game.application.MatchApplicationService.CurrentMatchSummary;
import com.romanysrael.battleofbluffs.game.application.MatchMode;
import com.romanysrael.battleofbluffs.game.application.PlayerMatchView;
import com.romanysrael.battleofbluffs.game.application.TimerMode;
import com.romanysrael.battleofbluffs.game.domain.Position;
import com.romanysrael.battleofbluffs.game.domain.Rank;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public final class MatchController {
    private final MatchApplicationService matches;

    public MatchController(MatchApplicationService matches) {
        this.matches = matches;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CommandResponse create(
            @RequestBody(required = false) CreateRequest request,
            Authentication authentication) {
        TimerMode timerMode = request == null || request.timerMode() == null
                ? TimerMode.CASUAL_UNTIMED
                : request.timerMode();
        MatchCommandResult result = matches.createMatch(new CreateMatchCommand(
                playerKey(authentication), MatchMode.CASUAL, timerMode));
        return response(result);
    }

    @PostMapping("/join")
    CommandResponse join(@Valid @RequestBody JoinRequest request, Authentication authentication) {
        return response(matches.joinMatch(new JoinMatchCommand(
                request.commandId(),
                request.roomCode(),
                playerKey(authentication),
                request.expectedVersion() == null ? 0 : request.expectedVersion())));
    }

    @GetMapping("/current")
    CurrentMatchesResponse current(Authentication authentication) {
        List<CurrentMatchSummary> activities = matches.currentMatches(playerKey(authentication));
        return new CurrentMatchesResponse(activities, activities.size() > 1);
    }

    @GetMapping("/{matchId}")
    PlayerMatchView view(@PathVariable UUID matchId, Authentication authentication) {
        return matches.getView(matchId, playerKey(authentication));
    }

    @PutMapping("/{matchId}/formation")
    CommandResponse formation(
            @PathVariable UUID matchId,
            @Valid @RequestBody FormationRequest request,
            Authentication authentication) {
        List<FormationPiece> pieces = request.pieces().stream()
                .map(piece -> new FormationPiece(
                        piece.pieceId(), piece.rank(), new Position(piece.row(), piece.column())))
                .toList();
        return response(matches.submitFormation(new SubmitFormationCommand(
                request.commandId(),
                matchId,
                playerKey(authentication),
                request.expectedVersion(),
                pieces)));
    }

    @PostMapping("/{matchId}/lock")
    CommandResponse lock(
            @PathVariable UUID matchId,
            @Valid @RequestBody CommandRequest request,
            Authentication authentication) {
        return response(matches.lockFormation(new LockFormationCommand(
                request.commandId(), matchId, playerKey(authentication), request.expectedVersion())));
    }

    @PostMapping("/{matchId}/moves")
    CommandResponse move(
            @PathVariable UUID matchId,
            @Valid @RequestBody MoveRequest request,
            Authentication authentication) {
        return response(matches.makeMove(new MakeMoveCommand(
                request.commandId(),
                matchId,
                playerKey(authentication),
                request.expectedVersion(),
                new Position(request.source().row(), request.source().column()),
                new Position(request.destination().row(), request.destination().column()))));
    }

    @PostMapping("/{matchId}/resign")
    CommandResponse resign(
            @PathVariable UUID matchId,
            @Valid @RequestBody CommandRequest request,
            Authentication authentication) {
        return response(matches.resign(new ResignCommand(
                request.commandId(), matchId, playerKey(authentication), request.expectedVersion())));
    }

    @PostMapping("/{matchId}/cancel")
    LifecycleResponse cancel(
            @PathVariable UUID matchId,
            @Valid @RequestBody CommandRequest request,
            Authentication authentication) {
        return lifecycle(matches.cancelMatch(new CancelMatchCommand(
                request.commandId(), matchId, playerKey(authentication), request.expectedVersion())));
    }

    @PostMapping("/{matchId}/leave")
    LifecycleResponse leave(
            @PathVariable UUID matchId,
            @Valid @RequestBody CommandRequest request,
            Authentication authentication) {
        return lifecycle(matches.leaveMatch(new LeaveMatchCommand(
                request.commandId(), matchId, playerKey(authentication), request.expectedVersion())));
    }

    private static String playerKey(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return principal.userId().toString();
    }

    private static CommandResponse response(MatchCommandResult result) {
        return new CommandResponse(
                result.commandId(),
                result.version(),
                result.view().matchId(),
                result.view().roomCode(),
                result.view());
    }

    private static LifecycleResponse lifecycle(MatchLifecycleResult result) {
        return new LifecycleResponse(
                result.commandId(), result.version(), result.matchId(), result.action());
    }

    public record CreateRequest(TimerMode timerMode) {
    }

    public record JoinRequest(
            @NotNull UUID commandId,
            @NotBlank String roomCode,
            @Positive Long expectedVersion) {
    }

    public record CommandRequest(@NotNull UUID commandId, @Positive long expectedVersion) {
    }

    public record Cell(@Min(0) @Max(7) int row, @Min(0) @Max(8) int column) {
    }

    public record FormationItem(
            @NotNull UUID pieceId,
            @NotNull Rank rank,
            @Min(0) @Max(7) int row,
            @Min(0) @Max(8) int column) {
    }

    public record FormationRequest(
            @NotNull UUID commandId,
            @Positive long expectedVersion,
            @NotNull @Size(min = 21, max = 21) List<@Valid FormationItem> pieces) {
        @Override public String toString() {
            return "FormationRequest[commandId=" + commandId + ", expectedVersion="
                    + expectedVersion + ", pieces=REDACTED]";
        }
    }

    public record MoveRequest(
            @NotNull UUID commandId,
            @Positive long expectedVersion,
            @NotNull @Valid Cell source,
            @NotNull @Valid Cell destination) {
    }

    public record CommandResponse(
            UUID commandId,
            long version,
            UUID matchId,
            String roomCode,
            PlayerMatchView view) {
    }

    public record CurrentMatchesResponse(
            List<CurrentMatchSummary> activities,
            boolean multipleOpenMatches) {
        public CurrentMatchesResponse {
            activities = List.copyOf(activities);
        }
    }

    public record LifecycleResponse(
            UUID commandId,
            long version,
            UUID matchId,
            MatchLifecycleResult.Action action) {
    }
}
