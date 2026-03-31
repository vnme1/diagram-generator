package com.ehapdls.diagram_generator.controller;

import com.ehapdls.diagram_generator.dto.ErrorResponse;
import com.ehapdls.diagram_generator.exception.DiagramGenerationException;
import com.ehapdls.diagram_generator.exception.GeminiApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value")
                .orElse("Invalid request");

        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(GeminiApiException.class)
    public ResponseEntity<ErrorResponse> handleGeminiApi(GeminiApiException ex) {
        log.error("Gemini API error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DiagramGenerationException.class)
    public ResponseEntity<ErrorResponse> handleDiagramGeneration(DiagramGenerationException ex) {
        log.warn("Diagram generation failed: {}", ex.getMessage());
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("An internal error occurred. Please try again."));
    }
}
