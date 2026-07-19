package com.romanysrael.battleofbluffs.game.web;

import com.romanysrael.battleofbluffs.game.application.*;
import com.romanysrael.battleofbluffs.matchmaking.MatchmakingException;
import com.romanysrael.battleofbluffs.user.AccountException;
import com.romanysrael.battleofbluffs.social.ChatException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public final class DevelopmentApiExceptionHandler {
    @ExceptionHandler(AccountException.class)
    ResponseEntity<ApiError> account(AccountException exception) {
        HttpStatus status = switch (exception.code()) {
            case "ACCOUNT_CONFLICT" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "AUTHENTICATION_REQUIRED", "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
            case "ACCOUNT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_TOKEN", "INVALID_ACCOUNT_INPUT" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(
                exception.code(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ChatException.class)
    ResponseEntity<ApiError> chat(ChatException exception) {
        HttpStatus status = switch (exception.code()) {
            case "CHAT_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "CHAT_BLOCKED" -> HttpStatus.FORBIDDEN;
            case "CHAT_UNAVAILABLE", "OPPONENT_UNAVAILABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(
                exception.code(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MatchmakingException.class)
    ResponseEntity<ApiError> matchmaking(MatchmakingException exception) {
        HttpStatus status = switch (exception.code()) {
            case "ACCOUNT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "EMAIL_VERIFICATION_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "ACTIVE_MATCH" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(
                exception.code(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MatchApplicationException.class)
    ResponseEntity<ApiError> application(MatchApplicationException exception) {
        HttpStatus status = switch (exception.code()) {
            case MATCH_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PLAYER_NOT_IN_MATCH, BLOCKED_RELATION -> HttpStatus.FORBIDDEN;
            case STALE_VERSION, COMMAND_CONFLICT, MATCH_FULL, ALREADY_LOCKED,
                    TERMINAL_MATCH, INVALID_ROOM_STATE -> HttpStatus.CONFLICT;
            case INVALID_FORMATION, ILLEGAL_MOVE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ResponseEntity.status(status).body(new ApiError(
                exception.code().name(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", message, Instant.now()));
    }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "INVALID_REQUEST", exception.getMessage(), Instant.now()));
    }

    public record ApiError(String code, String message, Instant timestamp) { }
}
