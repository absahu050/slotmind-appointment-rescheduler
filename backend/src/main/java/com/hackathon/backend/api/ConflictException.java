package com.hackathon.backend.api;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
