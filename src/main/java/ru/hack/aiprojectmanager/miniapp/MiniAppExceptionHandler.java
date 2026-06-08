package ru.hack.aiprojectmanager.miniapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "ru.hack.aiprojectmanager.miniapp")
public class MiniAppExceptionHandler {

    @ExceptionHandler(MiniAppException.class)
    public ResponseEntity<Map<String, String>> handleMiniApp(MiniAppException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception e) {
        log.error("Unhandled error in mini-app API", e);
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
    }
}
