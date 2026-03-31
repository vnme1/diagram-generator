package com.ehapdls.diagram_generator.exception;

public class DiagramGenerationException extends RuntimeException {

    public DiagramGenerationException(String message) {
        super(message);
    }

    public DiagramGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
