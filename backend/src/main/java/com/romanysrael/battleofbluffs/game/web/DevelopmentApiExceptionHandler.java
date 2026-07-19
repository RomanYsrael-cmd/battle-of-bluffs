package com.romanysrael.battleofbluffs.game.web;

import com.romanysrael.battleofbluffs.game.application.*;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public final class DevelopmentApiExceptionHandler {
    @ExceptionHandler(MatchApplicationException.class)
    ResponseEntity<ApiError> application(MatchApplicationException exception) {
        HttpStatus status = switch (exception.code()) {
            case MATCH_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PLAYER_NOT_IN_MATCH -> HttpStatus.FORBIDDEN;
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
