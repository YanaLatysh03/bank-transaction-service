package com.latysh.bank.transactionservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(RateNotFoundException.class)
    ResponseEntity<ProblemDetail> handleRateNotFound(RateNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail body = body(HttpStatus.BAD_REQUEST, "Тело запроса не прошло проверку", request);
        body.setProperty("errors", violations(exception));
        return response(HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Тело запроса не удалось прочитать", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, HttpServletRequest request) {
        return response(status, body(status, detail, request));
    }

    private static ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail body) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body);
    }

    private static ProblemDetail body(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static List<Map<String, String>> violations(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.add(violation(snakeCase(error.getField()), error.getDefaultMessage()));
        }
        for (ObjectError error : exception.getBindingResult().getGlobalErrors()) {
            errors.add(violation(error.getObjectName(), error.getDefaultMessage()));
        }
        return errors;
    }

    private static Map<String, String> violation(String field, String message) {
        Map<String, String> violation = new LinkedHashMap<>();
        violation.put("field", field);
        violation.put("message", message == null ? "" : message);
        return violation;
    }

    /**
     * Конвертация полей из snakeCase в snake_case
     */
    private static String snakeCase(String name) {
        StringBuilder result = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(current));
        }
        return result.toString();
    }
}
