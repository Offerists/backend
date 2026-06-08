package ru.hack.aiprojectmanager.miniapp;

import org.springframework.http.HttpStatus;

public class MiniAppException extends RuntimeException {

    private final HttpStatus status;

    public MiniAppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static MiniAppException notFound(String message) {
        return new MiniAppException(HttpStatus.NOT_FOUND, message);
    }

    public static MiniAppException badRequest(String message) {
        return new MiniAppException(HttpStatus.BAD_REQUEST, message);
    }

    public static MiniAppException conflict(String message) {
        return new MiniAppException(HttpStatus.CONFLICT, message);
    }
}
